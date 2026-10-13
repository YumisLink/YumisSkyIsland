package cn.yumis.skyisland.data;

import cn.yumis.skyisland.config.GameConfig;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.zip.CRC32;

/** Append, force to disk, then publish on the server thread. A torn final record is ignored. */
public final class DurableStore implements AutoCloseable {
    private final ExecutorService io = Executors.newSingleThreadExecutor(Thread.ofPlatform().name("yumis-journal").factory());
    private final Consumer<Runnable> server;
    private final FileChannel journal;
    private final Path directory;
    private IslandState state;
    private boolean busy;
    private volatile Throwable failure;
    private Pending pending;
    private static final class Pending {
        final IslandState next; final Runnable after; final CompletableFuture<Void> durable = new CompletableFuture<>();
        Pending(IslandState next,Runnable after) { this.next = next; this.after = after; }
    }
    public DurableStore(Path directory, Consumer<Runnable> server) throws IOException {
        this.server = server;
        this.directory = directory;
        Files.createDirectories(directory);
        Path file = directory.resolve("state.journal");
        state = new IslandState();
        Path checkpoint = directory.resolve("state.checkpoint");
        if (Files.exists(checkpoint)) {
            try (DataInputStream input = new DataInputStream(Files.newInputStream(checkpoint))) {
                int length = input.readInt(); long checksum = Integer.toUnsignedLong(input.readInt());
                if (length < 1 || length > 64*1024*1024) throw new IOException("Invalid island checkpoint size");
                byte[] data = input.readNBytes(length); CRC32 crc = new CRC32(); crc.update(data);
                if (data.length != length || crc.getValue() != checksum) throw new IOException("Corrupted island checkpoint");
                state = GameConfig.JSON.fromJson(new String(data,StandardCharsets.UTF_8),IslandState.class);
            }
        }
        long validEnd = 0;
        if (Files.exists(file)) {
            try (RandomAccessFile in = new RandomAccessFile(file.toFile(), "r")) {
                while (in.getFilePointer() < in.length()) {
                    long start = in.getFilePointer();
                    if (in.length() - start < 8) break;
                    int length = in.readInt(); long checksum = Integer.toUnsignedLong(in.readInt());
                    if (length <= 0 || length > 64 * 1024 * 1024 || in.length() - in.getFilePointer() < length) break;
                    byte[] bytes = new byte[length]; in.readFully(bytes);
                    CRC32 crc = new CRC32(); crc.update(bytes);
                    if (crc.getValue() != checksum) {
                        // Never silently discard valid records following corrupted data.
                        if (in.getFilePointer() < in.length()) throw new IOException("Corrupted island journal at " + start);
                        break;
                    }
                    IslandState read = GameConfig.JSON.fromJson(new String(bytes, StandardCharsets.UTF_8), IslandState.class);
                    if (read.sequence > state.sequence) state = read;
                    validEnd = in.getFilePointer();
                }
            }
        }
        journal = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
        journal.truncate(validEnd); journal.position(validEnd);
    }
    public IslandState state() { return state; }
    public boolean available() { return !busy && failure == null; }
    public Throwable failure() { return failure; }
    public void commit(Consumer<IslandState> edit, Runnable after, Consumer<Throwable> failed) {
        if (!available()) throw new IllegalStateException("Island data is busy or unavailable");
        IslandState next = GameConfig.JSON.fromJson(GameConfig.JSON.toJson(state), IslandState.class);
        edit.accept(next); next.sequence = Math.addExact(state.sequence, 1);
        byte[] bytes = GameConfig.JSON.toJson(next).getBytes(StandardCharsets.UTF_8);
        busy = true;
        Pending transaction = new Pending(next,after); pending = transaction;
        io.execute(() -> {
            try {
                CRC32 crc = new CRC32(); crc.update(bytes);
                writeFrame(journal,bytes); journal.force(true);
                if (next.sequence % 128 == 0) compact(bytes);
                transaction.durable.complete(null);
                server.accept(() -> publish(transaction));
            } catch (Throwable e) {
                failure = e;
                transaction.durable.completeExceptionally(e);
                server.accept(() -> { busy = false; failed.accept(e); });
            }
        });
    }
    private void publish(Pending transaction) {
        if (pending != transaction) return;
        transaction.next.effectiveTicks = Math.max(transaction.next.effectiveTicks,state.effectiveTicks);
        state = transaction.next; pending = null; busy = false; transaction.after.run();
    }
    public void finishPending() throws IOException {
        Pending transaction = pending; if (transaction == null) return;
        try { transaction.durable.get(30,TimeUnit.SECONDS); publish(transaction); }
        catch (Exception e) { throw new IOException("Cannot finish island transaction",e); }
    }
    private static void writeFrame(FileChannel channel,byte[] bytes) throws IOException {
        if (bytes.length > 64*1024*1024) throw new IOException("Island state is too large");
        CRC32 crc = new CRC32(); crc.update(bytes); ByteBuffer frame = ByteBuffer.allocate(bytes.length+8).putInt(bytes.length).putInt((int)crc.getValue()).put(bytes);
        frame.flip(); while (frame.hasRemaining()) channel.write(frame);
    }
    private void compact(byte[] bytes) throws IOException {
        Path temporary = directory.resolve("state.checkpoint.tmp"), target = directory.resolve("state.checkpoint");
        try (FileChannel file = FileChannel.open(temporary,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING,StandardOpenOption.WRITE)) { writeFrame(file,bytes); file.force(true); }
        try { Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException e) { Files.move(temporary,target,StandardCopyOption.REPLACE_EXISTING); }
        journal.truncate(0); journal.position(0); journal.force(true);
    }
    @Override public void close() throws IOException {
        finishPending();
        io.shutdown();
        try { if (!io.awaitTermination(30, TimeUnit.SECONDS)) throw new IOException("Journal did not stop"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException(e); }
        if (failure != null) { journal.close(); throw new IOException("Island journal failed",failure); }
        state.sequence++; writeFrame(journal,GameConfig.JSON.toJson(state).getBytes(StandardCharsets.UTF_8)); journal.force(true);
        compact(GameConfig.JSON.toJson(state).getBytes(StandardCharsets.UTF_8)); journal.close();
    }
}
