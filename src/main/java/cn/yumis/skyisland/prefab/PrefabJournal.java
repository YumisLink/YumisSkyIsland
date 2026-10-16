package cn.yumis.skyisland.prefab;

import cn.yumis.skyisland.config.GameConfig;
import com.google.gson.*;
import java.io.*;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.zip.CRC32;

/** Small operation records, forced before mutating world or source inventories. */
public final class PrefabJournal implements AutoCloseable {
    private final Path root;
    private final ExecutorService writer=Executors.newSingleThreadExecutor(Thread.ofPlatform().name("prefab-journal").factory());
    private final Consumer<Runnable> server;
    private record Pending(Runnable after,Consumer<Throwable> failed) {}
    private final Map<CompletableFuture<Void>,Pending> pending=new ConcurrentHashMap<>();
    private final Map<String,Long> lastOffsets=new ConcurrentHashMap<>();
    public long lastOffset(String id){return lastOffsets.get(id);}
    public record Index(List<Long> offsets,Map<Long,List<Long>> chunks,JsonObject last) {}
    public PrefabJournal(Path root,Consumer<Runnable> server) throws IOException{this.root=root;this.server=server;Files.createDirectories(root);}
    public List<JsonObject> read(String id) throws IOException {
        Path path=path(id);if(!Files.exists(path))return List.of();List<JsonObject> frames=new ArrayList<>();long valid=0;
        try(RandomAccessFile in=new RandomAccessFile(path.toFile(),"r")){
            while(in.getFilePointer()<in.length()){
                if(in.length()-in.getFilePointer()<8)break;int size=in.readInt(),checksum=in.readInt();
                if(size<1||size>8*1024*1024)throw new IOException("预制施工日志长度损坏："+id);
                if(in.length()-in.getFilePointer()<size)break;byte[] bytes=new byte[size];in.readFully(bytes);CRC32 crc=new CRC32();crc.update(bytes);
                if((int)crc.getValue()!=checksum){if(in.getFilePointer()!=in.length())throw new IOException("预制施工日志校验失败："+id);break;}
                frames.add(JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject());valid=in.getFilePointer();
            }
        }
        try(var channel=FileChannel.open(path,StandardOpenOption.WRITE)){channel.truncate(valid);}return frames;
    }
    private Path path(String id){return root.resolve(UUID.fromString(id)+".journal");}
    public Index index(String id) throws IOException {
        Path path=path(id);List<Long> offsets=new ArrayList<>();Map<Long,List<Long>> chunks=new LinkedHashMap<>();JsonObject last=null;long valid=0;
        if(!Files.exists(path))return new Index(offsets,chunks,null);
        try(RandomAccessFile in=new RandomAccessFile(path.toFile(),"r")){
            while(in.getFilePointer()<in.length()){
                long offset=in.getFilePointer();if(in.length()-offset<8)break;int size=in.readInt(),checksum=in.readInt();
                if(size<1||size>8*1024*1024)throw new IOException("施工日志记录过大或损坏");if(in.length()-in.getFilePointer()<size)break;
                byte[] data=new byte[size];in.readFully(data);CRC32 crc=new CRC32();crc.update(data);
                if((int)crc.getValue()!=checksum){if(in.getFilePointer()<in.length())throw new IOException("施工日志校验失败");break;}
                last=JsonParser.parseString(new String(data,StandardCharsets.UTF_8)).getAsJsonObject();if(last.has("seq")&&last.get("seq").getAsLong()!=offsets.size()+1L)throw new IOException("施工日志序号不连续");offsets.add(offset);
                if(last.has("groups"))for(var group:last.getAsJsonArray("groups")){long chunk=group.getAsJsonObject().get("chunk").getAsLong();chunks.computeIfAbsent(chunk,_ ->new ArrayList<>()).add(offset);}
                valid=in.getFilePointer();
            }
        }
        try(var channel=FileChannel.open(path,StandardOpenOption.WRITE)){channel.truncate(valid);}return new Index(offsets,chunks,last);
    }
    public JsonObject at(String id,long offset) throws IOException {
        try(RandomAccessFile in=new RandomAccessFile(path(id).toFile(),"r")){if(offset<0||in.length()-offset<8)throw new IOException("施工日志位置无效");in.seek(offset);int size=in.readInt(),checksum=in.readInt();if(size<1||size>8*1024*1024||in.length()-in.getFilePointer()<size)throw new IOException("施工日志记录长度无效");byte[] data=new byte[size];in.readFully(data);CRC32 crc=new CRC32();crc.update(data);if((int)crc.getValue()!=checksum)throw new IOException("施工日志记录校验失败");return JsonParser.parseString(new String(data,StandardCharsets.UTF_8)).getAsJsonObject();}
    }
    public CompletableFuture<Void> append(String id,JsonObject frame,Runnable after,Consumer<Throwable> failed) {
        byte[] bytes=GameConfig.JSON.toJson(frame).getBytes(StandardCharsets.UTF_8);
        CompletableFuture<Void> future=CompletableFuture.runAsync(()->{
            try(var out=FileChannel.open(path(id),StandardOpenOption.CREATE,StandardOpenOption.WRITE,StandardOpenOption.APPEND)){
                lastOffsets.put(id,out.size());
                CRC32 crc=new CRC32();crc.update(bytes);ByteBuffer data=ByteBuffer.allocate(bytes.length+8).putInt(bytes.length).putInt((int)crc.getValue()).put(bytes);data.flip();while(data.hasRemaining())out.write(data);out.force(true);
            }catch(IOException e){throw new CompletionException(e);}
        },writer);
        pending.put(future,new Pending(after,failed));future.whenComplete((_,error)->server.accept(()->publish(future,error)));return future;
    }
    private void publish(CompletableFuture<Void> future,Throwable error){Pending entry=pending.remove(future);if(entry!=null){if(error==null)entry.after.run();else entry.failed.accept(error);}}
    @Override public void close() throws IOException {
        writer.shutdown();try{if(!writer.awaitTermination(30,TimeUnit.SECONDS))throw new IOException("预制施工日志未能保存完毕");}
        catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException(e);}
        for(var future:List.copyOf(pending.keySet()))try{future.join();publish(future,null);}catch(CompletionException e){publish(future,e);throw new IOException("预制施工日志保存失败",e);}
    }
}
