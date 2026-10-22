import { Card, CardHeader, CardTitle } from "./card";
import { carrierLabel, shipmentStatusLabel } from "./fulfillment-badges";
import { formatDateTime } from "./lib/format";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "./table";

/** fulfillment 배송 조회의 배송 하나(docs/architecture/fulfillment.md "API"). */
export type ShipmentSummaryView = {
  shipmentId?: string;
  status?: string;
  carrier?: string;
  trackingNumber?: string;
  acceptedAt?: string;
  shippedAt?: string;
  deliveredAt?: string;
};

/** 하위 주문의 배송 이력. 만든 순서이고, 첫 배송 뒤의 것은 교환 재발송이다. 구매자·판매자 화면이 쓴다. */
export function ShipmentTable({ shipments }: { shipments: ShipmentSummaryView[] }) {
  return (
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
  );
}
