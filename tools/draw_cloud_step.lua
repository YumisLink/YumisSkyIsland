-- Original 18x18 status icon: vanilla-style upward arrow over a shaded cloud.
-- Run with Aseprite from the project root. All pixels are drawn without scaling.
local root = app.params["project-root"] or CLOUD_STEP_PROJECT_ROOT or "."
local rows = {
  "..................",
  "........HH........",
  ".......HWWD.......",
  "......HWWWDD......",
  ".....HWWWWWDD.....",
  "....HWWWWWWDDD....",
  "....DDDDWWDDDD....",
  "........WW........",
  "........WD........",
  ".....HH.WD.HH.....",
  "....HWWHDHHWWH....",
  "..HHWWWWWWWWWHD...",
  ".HWWWWWWWWWWWWWD..",
  ".HWWWWWWWWWWWSDD..",
  "..HSSSSSSSSSSDD...",
  "...DDDDDDDDDDD....",
  "..................",
  ".................."
}
local colors = {
  H = app.pixelColor.rgba(233, 255, 255, 255),
  W = app.pixelColor.rgba(255, 255, 255, 255),
  S = app.pixelColor.rgba(189, 211, 212, 255),
  D = app.pixelColor.rgba(89, 153, 177, 255)
}
local sprite = Sprite(18, 18, ColorMode.RGB)
sprite.layers[1].name = "Cloud Step"
local image = sprite.cels[1].image
for y, row in ipairs(rows) do
  assert(#row == 18, "Invalid pixel row " .. y)
  for x = 1, #row do
    local color = colors[row:sub(x, x)]
    if color then image:drawPixel(x - 1, y - 1, color) end
  end
end
sprite:saveAs(root .. "/art/mob_effect/cloud_step.aseprite")
sprite:saveCopyAs(root .. "/src/main/resources/assets/yumis_sky_island/textures/mob_effect/cloud_step.png")
print("Created Cloud Step: 18x18 transparent PNG and editable Aseprite source")
