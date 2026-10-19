"""file 서비스 테스트 이미지. /out에 쓴다."""
from PIL import Image, ImageDraw

out = "/out/"

# 큰 가로 사진(서브샘플링 디코딩 경로)
img = Image.new("RGB", (2400, 1600))
d = ImageDraw.Draw(img)
for x in range(0, 2400, 40):
    d.rectangle([x, 0, x + 39, 1599], fill=(x % 256, (x // 3) % 256, 200))
img.save(out + "landscape.jpg", quality=85)

# EXIF 방향 6(시계 방향 90도). 저장된 픽셀은 120x80, 왼쪽 빨강·오른쪽 파랑. 바로 세우면 80x120, 위 빨강·아래 파랑.
# GPS 정보도 넣어서 썸네일에서 빠지는지 본다.
img = Image.new("RGB", (120, 80), (0, 0, 255))
ImageDraw.Draw(img).rectangle([0, 0, 59, 79], fill=(255, 0, 0))
exif = Image.Exif()
exif[0x0112] = 6
exif[0x8825] = {1: "N", 2: (37.0, 33.0, 0.0), 3: "E", 4: (126.0, 58.0, 0.0)}
img.save(out + "exif-rotate-90.jpg", quality=95, exif=exif)

# CMYK JPEG(JDK 기본 리더가 읽지 못한다)
Image.new("CMYK", (200, 100), (0, 255, 255, 0)).save(out + "cmyk.jpg", quality=90)

# 투명 PNG. 바깥은 투명, 가운데 빨간 사각형
img = Image.new("RGBA", (200, 200), (0, 0, 0, 0))
ImageDraw.Draw(img).rectangle([50, 50, 149, 149], fill=(255, 0, 0, 255))
img.save(out + "alpha.png")

# WebP(손실)
Image.new("RGB", (400, 300), (0, 160, 0)).save(out + "sample.webp", quality=80)

# 1억 화소 PNG(압축하면 작다). 픽셀 상한을 넘는다
Image.new("1", (10000, 10000), 0).save(out + "huge-dimensions.png", optimize=True)

# GIF(허용하지 않는 형식)
Image.new("RGB", (10, 10), (1, 2, 3)).save(out + "small.gif")

# 이미지가 아닌 내용과, 머리만 맞고 잘린 JPEG
open(out + "not-image.jpg", "wb").write(b"hello, this is not an image\n" * 10)
open(out + "truncated.jpg", "wb").write(open(out + "landscape.jpg", "rb").read()[:300])
