import type { ReactNode } from "react";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "./card";
import { ClaimBadge, claimReasonLabel, faultLabel } from "./fulfillment-badges";
import { formatDateTime, formatPrice } from "./lib/format";

/** fulfillment 클레임 조회의 필드(docs/architecture/fulfillment.md "API"). 세 앱이 같은 응답을 그린다. */
export type ClaimSummaryView = {
  claimId?: string;
  type?: string;
  status?: string;
  reason?: string;
  fault?: string;
  description?: string;
  fileIds?: string[];
  restock?: boolean;
  refundAmount?: number;
  rejectionReason?: string;
  mediationReason?: string;
  requestedAt?: string;
  approvedAt?: string;
  rejectedAt?: string;
  mediationRequestedAt?: string;
  completedAt?: string;
};

type ClaimCardProps = {
  claim: ClaimSummaryView;
  /**
   * 클레임 사진 Route Handler(BFF). `{imageBase}/{fileId}/{w300|w800}`로 받는다. 구매자·판매자는 `/api/claim-images`, 관리자는
   * 관리자 경로를 부르는 앱의 경로다
   */
  imageBase?: string;
  /** 카드 아래에 붙일 동작(승인·거부, 중재 요청) */
  children?: ReactNode;
};

/** 클레임 하나. 사유·귀책·설명·사진과 처리 결과(재입고, 환불, 거부, 중재)를 보여 준다. */
export function ClaimCard({ claim, imageBase = "/api/claim-images", children }: ClaimCardProps) {
  const photos = claim.fileIds ?? [];
  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-base">
          클레임 <ClaimBadge claim={claim} />
        </CardTitle>
        <CardDescription>신청 {formatDateTime(claim.requestedAt)}</CardDescription>
      </CardHeader>
      <CardContent className="grid gap-4 text-sm">
        <dl className="grid gap-x-6 gap-y-2 sm:grid-cols-[8rem_1fr]">
          <Row label="사유">
            {claimReasonLabel(claim.reason)} (귀책 {faultLabel(claim.fault)})
          </Row>
          {claim.description ? (
            <Row label="설명">
              <span className="whitespace-pre-wrap">{claim.description}</span>
            </Row>
          ) : null}
          {claim.type === "RETURN" && claim.restock !== undefined ? (
            <Row label="재입고">{claim.restock ? "한다" : "하지 않는다"}</Row>
          ) : null}
          {claim.refundAmount !== undefined ? (
            <Row label="환불 금액">{formatPrice(claim.refundAmount)}</Row>
          ) : null}
          {claim.rejectionReason ? (
            <Row label="거부 사유">
              {claim.rejectionReason} ({formatDateTime(claim.rejectedAt)})
            </Row>
          ) : null}
          {claim.mediationRequestedAt ? (
            <Row label="중재 요청">{formatDateTime(claim.mediationRequestedAt)}</Row>
          ) : null}
          {claim.mediationReason ? (
            <Row label="중재 결과">거부 확정. {claim.mediationReason}</Row>
          ) : null}
          {claim.approvedAt ? <Row label="승인">{formatDateTime(claim.approvedAt)}</Row> : null}
          {claim.completedAt ? <Row label="완료">{formatDateTime(claim.completedAt)}</Row> : null}
        </dl>
        {photos.length > 0 ? (
          <ul aria-label="클레임 사진" className="flex flex-wrap gap-2">
            {photos.map((fileId, index) => (
              <li key={fileId}>
                <a
                  href={`${imageBase}/${fileId}/w800`}
                  target="_blank"
                  rel="noreferrer"
                  className="block"
                >
                  {/* eslint-disable-next-line @next/next/no-img-element -- 비공개 변형 JPEG을 BFF로 그대로 받는다. */}
                  <img
                    src={`${imageBase}/${fileId}/w300`}
                    alt={`클레임 사진 ${index + 1}`}
                    width={96}
                    height={96}
                    loading="lazy"
                    className="size-24 rounded-md border bg-muted object-cover"
                  />
                </a>
              </li>
            ))}
          </ul>
        ) : null}
        {children}
      </CardContent>
    </Card>
  );
}

function Row({ label, children }: { label: string; children: ReactNode }) {
  return (
    <>
      <dt className="text-muted-foreground">{label}</dt>
      <dd>{children}</dd>
    </>
  );
}
