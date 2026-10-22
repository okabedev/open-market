import Link from "next/link";
import { notFound } from "next/navigation";
import {
  Alert,
  Button,
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
  carrierLabel,
  ClaimBadge,
  claimReasonLabel,
  ConfirmActionForm,
  faultLabel,
  formatDateTime,
  formatPrice,
  PageHeader,
  ProgressBadge,
  shipmentStatusLabel,
  ShippingCard,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@openmarket/ui";
import type { FulfillmentComponents } from "@openmarket/api-client";
import type { ReactNode } from "react";
import { bff } from "@/lib/bff";
import { orderNotice } from "@/lib/order-notice";
import { requireSeller } from "@/lib/session";
import { acceptOrder } from "../actions";
import { ClaimDecision } from "./claim-decision";
import { ShipForm } from "./ship-form";

/**
 * 본인 하위 주문 상세와 처리(ORD-4, FUL-1, CLM-3). 항목과 배송지는 order, 배송과 클레임은 fulfillment에서 읽는다. 판매자 동작은
 * 하위 주문의 마지막 배송에 한다(docs/architecture/fulfillment.md "배송"). 판매자는 다른 판매자의 항목, 주문 전체 금액, 구매자를
 * 보지 않는다(order.md "조회").
 */
export default async function SellerOrderPage({
  params,
  searchParams,
}: PageProps<"/orders/[sellerOrderId]">) {
  const { sellerOrderId } = await params;
  await requireSeller(`/orders/${sellerOrderId}`);
  const notice = orderNotice((await searchParams).notice);
  const [order, fulfillment] = await Promise.all([
    bff.serviceClient("order"),
    bff.serviceClient("fulfillment"),
  ]);
  const [{ data, response }, progress] = await Promise.all([
    order.GET("/v1/seller/orders/{sellerOrderId}", {
      params: { path: { sellerOrderId } },
    }),
    fulfillment.GET("/v1/seller/seller-orders/{sellerOrderId}", {
      params: { path: { sellerOrderId } },
    }),
  ]);
  if (response.status === 404 || response.status === 400) {
    notFound();
  }
  if (!data) {
    return <Alert variant="destructive">주문을 불러오지 못했습니다.</Alert>;
  }
  return (
    <>
      <PageHeader
        title={`주문 ${data.orderId}`}
        description={`결제 시각 ${formatDateTime(data.paidAt)} · 주문 시각 ${formatDateTime(data.placedAt)}`}
        actions={
          <Button asChild variant="outline">
            <Link href="/orders">목록</Link>
          </Button>
        }
      />
      {notice ? (
        <Alert variant={notice.error ? "destructive" : "success"} className="mb-4">
          {notice.message}
        </Alert>
      ) : null}
      <div className="grid gap-4">
        <Card>
          <CardContent className="flex flex-wrap items-center gap-2 pt-6 text-sm">
            <span className="text-muted-foreground">진행 상태</span>
            <ProgressBadge progress={data.progress} />
            <ClaimBadge claim={data.claim} />
          </CardContent>
        </Card>
        {progress.data ? (
          <Fulfillment sellerOrderId={sellerOrderId} view={progress.data} />
        ) : progress.response.status === 404 ? (
          <Alert>배송 정보를 준비하고 있습니다. 잠시 뒤 새로 고쳐 주세요.</Alert>
        ) : (
          <Alert variant="destructive">배송 정보를 불러오지 못했습니다.</Alert>
        )}
        <Card>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>상품</TableHead>
                <TableHead>옵션</TableHead>
                <TableHead className="text-right">개당 가격</TableHead>
                <TableHead className="text-right">수량</TableHead>
                <TableHead className="text-right">금액</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {(data.lines ?? []).map((line) => (
                <TableRow key={line.orderLineId}>
                  <TableCell>
                    <Link
                      href={`/products/${line.productId}`}
                      className="font-medium hover:underline"
                    >
                      {line.productName}
                    </Link>
                  </TableCell>
                  <TableCell>{(line.optionValues ?? []).join(" / ") || "-"}</TableCell>
                  <TableCell className="text-right whitespace-nowrap">
                    {formatPrice(line.unitPrice)}
                  </TableCell>
                  <TableCell className="text-right">{line.quantity}</TableCell>
                  <TableCell className="text-right whitespace-nowrap">
                    {formatPrice(line.amount)}
                  </TableCell>
                </TableRow>
              ))}
              <TableRow>
                <TableCell colSpan={4} className="text-right font-semibold">
                  하위 주문 금액
                </TableCell>
                <TableCell className="text-right font-semibold whitespace-nowrap">
                  {formatPrice(data.amount)}
                </TableCell>
              </TableRow>
            </TableBody>
          </Table>
        </Card>
        <ShippingCard shipping={data.shipping} />
      </div>
    </>
  );
}

type FulfillmentView = FulfillmentComponents["schemas"]["SellerFulfillmentView"];
type ClaimView = FulfillmentComponents["schemas"]["ClaimView"];

/** 배송 처리(발주 확인, 발송)와 배송 이력, 클레임. */
function Fulfillment({ sellerOrderId, view }: { sellerOrderId: string; view: FulfillmentView }) {
  const shipments = view.shipments ?? [];
  const last = shipments.at(-1);
  const reshipment = shipments.length > 1;
  const claims = [...(view.claims ?? [])].reverse();
  return (
    <>
      {last?.status === "READY" || last?.status === "ACCEPTED" ? (
        <Card>
          <CardHeader>
            <CardTitle className="text-base">
              {last.status === "READY" ? "발주 확인" : "발송"}
              {reshipment ? " (교환 재발송)" : ""}
            </CardTitle>
            <CardDescription>
              {last.status === "READY"
                ? "주문을 확인했으면 발주 확인을 눌러 주세요. 구매자에게 상품 준비 중으로 보입니다. 발송 전까지는 구매자가 취소할 수 있습니다."
                : "상품을 보냈으면 택배사와 송장 번호를 넣어 주세요."}
            </CardDescription>
          </CardHeader>
          <CardContent>
            {last.status === "READY" ? (
              <ConfirmActionForm
                action={acceptOrder}
                hidden={{ sellerOrderId }}
                title="발주를 확인할까요?"
                description="구매자에게 상품 준비 중으로 보입니다."
                confirmText="발주 확인"
              >
                발주 확인
              </ConfirmActionForm>
            ) : (
              <ShipForm sellerOrderId={sellerOrderId} />
            )}
          </CardContent>
        </Card>
      ) : null}
      <Card>
        <CardHeader>
          <CardTitle className="text-base">배송</CardTitle>
        </CardHeader>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>구분</TableHead>
              <TableHead>상태</TableHead>
              <TableHead>송장</TableHead>
              <TableHead>발주 확인</TableHead>
              <TableHead>발송</TableHead>
              <TableHead>배송 완료</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {shipments.map((shipment, index) => (
              <TableRow key={shipment.shipmentId}>
                <TableCell>{index === 0 ? "첫 배송" : "교환 재발송"}</TableCell>
                <TableCell>{shipmentStatusLabel(shipment.status)}</TableCell>
                <TableCell className="whitespace-nowrap">
                  {shipment.trackingNumber
                    ? `${carrierLabel(shipment.carrier)} ${shipment.trackingNumber}`
                    : "-"}
                </TableCell>
                <TableCell className="whitespace-nowrap">
                  {formatDateTime(shipment.acceptedAt)}
                </TableCell>
                <TableCell className="whitespace-nowrap">
                  {formatDateTime(shipment.shippedAt)}
                </TableCell>
                <TableCell className="whitespace-nowrap">
                  {formatDateTime(shipment.deliveredAt)}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Card>
      {claims.map((claim) => (
        <ClaimCard key={claim.claimId} sellerOrderId={sellerOrderId} claim={claim} />
      ))}
    </>
  );
}

function ClaimCard({ sellerOrderId, claim }: { sellerOrderId: string; claim: ClaimView }) {
  const waiting =
    claim.status === "REQUESTED" && (claim.type === "RETURN" || claim.type === "EXCHANGE");
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
        {(claim.fileIds ?? []).length > 0 ? (
          <ul aria-label="클레임 사진" className="flex flex-wrap gap-2">
            {(claim.fileIds ?? []).map((fileId, index) => (
              <li key={fileId}>
                <a
                  href={`/api/claim-images/${fileId}/w800`}
                  target="_blank"
                  rel="noreferrer"
                  className="block"
                >
                  {/* eslint-disable-next-line @next/next/no-img-element -- 비공개 변형 JPEG을 BFF로 그대로 받는다. */}
                  <img
                    src={`/api/claim-images/${fileId}/w300`}
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
        {waiting ? (
          <ClaimDecision
            sellerOrderId={sellerOrderId}
            claimId={claim.claimId ?? ""}
            type={claim.type as "RETURN" | "EXCHANGE"}
          />
        ) : null}
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
