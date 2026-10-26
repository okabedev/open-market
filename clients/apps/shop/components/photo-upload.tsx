"use client";

import { Button, Input, Label } from "@openmarket/ui";
import { useState } from "react";
import { PHOTO_MAX_BYTES, type UploadPurpose } from "@/lib/upload";

type PhotoUploadProps = {
  purpose: UploadPurpose;
  /** 고를 수 있는 사진 수 */
  limit: number;
  photos: string[];
  onPhotosChange: (update: (current: string[]) => string[]) => void;
  onUploadingChange: (uploading: boolean) => void;
  /** 올린 사진의 미리 보기 주소. 클레임 사진은 BFF 비공개 경로, 리뷰 사진은 공개 경로다 */
  previewSrc: (fileId: string) => string;
  /** 형식·수·크기 안내 뒤에 붙는 문장(누가 보는지) */
  audience: string;
  error?: string;
};

/**
 * 사진 고르기(클레임·리뷰). 고르는 즉시 BFF(`/api/files?purpose=`)로 한 장씩 올리고 파일 ID만 갖는다. 폼은 파일 ID를 숨은 칸으로
 * 보낸다. 형식 검사는 file이 하고, 여기서는 수와 크기만 미리 본다.
 */
export function PhotoUpload({
  purpose,
  limit,
  photos,
  onPhotosChange,
  onUploadingChange,
  previewSrc,
  audience,
  error,
}: PhotoUploadProps) {
  const [uploading, setUploading] = useState(false);
  const [uploadError, setUploadError] = useState<string | null>(null);

  function changeUploading(value: boolean) {
    setUploading(value);
    onUploadingChange(value);
  }

  async function upload(files: FileList | null) {
    if (!files || files.length === 0) {
      return;
    }
    setUploadError(null);
    changeUploading(true);
    let count = photos.length;
    try {
      for (const file of Array.from(files)) {
        if (count >= limit) {
          setUploadError(`사진은 ${limit}장까지입니다.`);
          break;
        }
        if (file.size > PHOTO_MAX_BYTES) {
          setUploadError(`${file.name}: 이미지는 10MB까지 올릴 수 있습니다.`);
          continue;
        }
        const body = new FormData();
        body.set("file", file);
        const response = await fetch(`/api/files?purpose=${purpose}`, { method: "POST", body });
        const result = (await response.json().catch(() => ({}))) as {
          fileId?: string;
          detail?: string;
        };
        if (!response.ok || !result.fileId) {
          setUploadError(`${file.name}: ${result.detail ?? "사진을 올리지 못했습니다."}`);
          continue;
        }
        const fileId = result.fileId;
        count += 1;
        onPhotosChange((current) => [...current, fileId]);
      }
    } finally {
      changeUploading(false);
    }
  }

  return (
    <div className="grid gap-2">
      <Label htmlFor="photos">사진(선택)</Label>
      {photos.length > 0 ? (
        <ul aria-label="올린 사진" className="flex flex-wrap gap-2">
          {photos.map((fileId, index) => (
            <li key={fileId} className="grid gap-1 text-xs">
              {/* eslint-disable-next-line @next/next/no-img-element -- 변형 JPEG을 그대로 받는다. */}
              <img
                src={previewSrc(fileId)}
                alt={`올린 사진 ${index + 1}`}
                width={96}
                height={96}
                className="size-24 rounded-md border bg-muted object-cover"
              />
              <Button
                type="button"
                size="sm"
                variant="outline"
                onClick={() => onPhotosChange((current) => current.filter((id) => id !== fileId))}
                aria-label={`사진 ${index + 1} 빼기`}
              >
                빼기
              </Button>
            </li>
          ))}
        </ul>
      ) : null}
      <Input
        id="photos"
        type="file"
        accept="image/jpeg,image/png,image/webp"
        multiple
        disabled={uploading || photos.length >= limit}
        aria-describedby="photos-hint"
        onChange={(event) => {
          void upload(event.currentTarget.files);
          event.currentTarget.value = "";
        }}
      />
      <p id="photos-hint" className="text-xs text-muted-foreground">
        JPEG, PNG, WebP {limit}장까지, 한 장에 10MB까지입니다. {audience}
      </p>
      {uploading ? <p className="text-xs text-muted-foreground">올리는 중…</p> : null}
      {uploadError ? <p className="text-xs text-destructive">{uploadError}</p> : null}
      {error ? <p className="text-xs text-destructive">{error}</p> : null}
    </div>
  );
}
