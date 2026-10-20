import { cn } from "@openmarket/ui";

type ProductImageProps = {
  fileId: string | undefined | null;
  /** 목록 썸네일은 w300, 크게 볼 때는 w800 */
  variant?: "w300" | "w800";
  size: number;
  alt: string;
  className?: string;
};

/**
 * 상품 이미지 변형. 같은 Origin의 `/files/{fileId}/{variant}`로 받는다(next.config.ts의 rewrites). 이미지가 없으면 빈 칸을
 * 그린다. 변형이 이미 줄인 JPEG이라 Next.js 이미지 최적화는 쓰지 않는다.
 */
export function ProductImage({
  fileId,
  variant = "w300",
  size,
  alt,
  className,
}: ProductImageProps) {
  const box = cn("shrink-0 rounded-md border bg-muted object-cover", className);
  if (!fileId) {
    return (
      <div
        role="img"
        aria-label={`${alt} (이미지 없음)`}
        className={box}
        style={{ width: size, height: size }}
      />
    );
  }
  return (
    // eslint-disable-next-line @next/next/no-img-element -- 변형 JPEG을 그대로 보여 준다(위 설명).
    <img
      src={`/files/${fileId}/${variant}`}
      alt={alt}
      width={size}
      height={size}
      loading="lazy"
      className={box}
      style={{ width: size, height: size }}
    />
  );
}
