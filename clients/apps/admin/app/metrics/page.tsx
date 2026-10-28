import {
  Alert,
  Button,
  Card,
  formatPrice,
  Input,
  Label,
  PageHeader,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@openmarket/ui";
import Link from "next/link";
import { bff } from "@/lib/bff";
import {
  addDays,
  DEFAULT_DAYS,
  formatRate,
  kstToday,
  MAX_DAYS,
  mergeDays,
  metricsPeriod,
  totals,
  type OrderDay,
  type PaymentDay,
} from "@/lib/metrics";
import { requireSession } from "@/lib/session";

const count = new Intl.NumberFormat("ko-KR");

/**
 * 운영 지표(ADM-4, ADR 0023). 날짜(KST)별 주문 수, 결제된 주문 수, GMV(order)와 결제 승인 시도 결과·실패율(payment)을 한 표로 보인다.
 * 두 서비스에 동시에 묻고, 한 서비스가 응답하지 않으면 나머지만 보이며 빠진 쪽을 알린다. 기간은 검색 조건(`from`, `to`)이다.
 */
export default async function MetricsPage({ searchParams }: PageProps<"/metrics">) {
  await requireSession("/metrics");
  const now = new Date();
  const period = metricsPeriod(await searchParams, now);
  return (
    <>
      <PageHeader
        title="운영 지표"
        description="날짜(한국 시간)별 주문과 결제입니다. 주문·결제 기록에서 그때그때 셉니다."
      />
      <PeriodForm from={period.from} to={period.to} today={kstToday(now)} />
      {period.error ? (
        <Alert variant="destructive">{period.error}</Alert>
      ) : (
        <Metrics from={period.from} to={period.to} />
      )}
    </>
  );
}

function PeriodForm({ from, to, today }: { from: string; to: string; today: string }) {
  const recent = (days: number) => `/metrics?from=${addDays(today, -(days - 1))}&to=${today}`;
  return (
    <form method="get" action="/metrics" className="mb-4 flex flex-wrap items-end gap-3">
      <div className="grid gap-1">
        <Label htmlFor="from">시작일</Label>
        <Input id="from" name="from" type="date" defaultValue={from} className="w-40" required />
      </div>
      <div className="grid gap-1">
        <Label htmlFor="to">종료일</Label>
        <Input id="to" name="to" type="date" defaultValue={to} className="w-40" required />
      </div>
      <Button type="submit">조회</Button>
      <Button asChild variant="outline">
        <Link href={recent(DEFAULT_DAYS)}>최근 {DEFAULT_DAYS}일</Link>
      </Button>
      <Button asChild variant="outline">
        <Link href={recent(MAX_DAYS)}>최근 {MAX_DAYS}일</Link>
      </Button>
      <p className="w-full text-sm text-muted-foreground">기간은 {MAX_DAYS}일까지입니다.</p>
    </form>
  );
}

async function Metrics({ from, to }: { from: string; to: string }) {
  const query = { params: { query: { from, to } } };
  const [orders, payments] = await Promise.all([
    bff
      .serviceClient("order")
      .then((order) => order.GET("/v1/admin/metrics/orders", query))
      .then(({ data, response }) => (response.ok ? (data?.days ?? null) : null))
      .catch(() => null),
    bff
      .serviceClient("payment")
      .then((payment) => payment.GET("/v1/admin/metrics/payments", query))
      .then(({ data, response }) => (response.ok ? (data?.days ?? null) : null))
      .catch(() => null),
  ]);
  if (!orders && !payments) {
    return <Alert variant="destructive">운영 지표를 불러오지 못했습니다.</Alert>;
  }
  const rows = mergeDays(from, to, orders, payments);
  const sum = totals(rows);
  return (
    <>
      {!orders ? (
        <Alert variant="destructive" className="mb-4">
          주문 지표를 불러오지 못했습니다(order). 결제 지표만 보입니다.
        </Alert>
      ) : null}
      {!payments ? (
        <Alert variant="destructive" className="mb-4">
          결제 지표를 불러오지 못했습니다(payment). 주문 지표만 보입니다.
        </Alert>
      ) : null}
      <Card>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>날짜</TableHead>
              <TableHead className="text-right">주문</TableHead>
              <TableHead className="text-right">결제된 주문</TableHead>
              <TableHead className="text-right">거래액(GMV)</TableHead>
              <TableHead className="text-right">결제 승인</TableHead>
              <TableHead className="text-right">결제 거절</TableHead>
              <TableHead className="text-right">미정</TableHead>
              <TableHead className="text-right">결제 실패율</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {rows.map((row) => (
              <MetricsCells
                key={row.date}
                label={row.date}
                order={row.order}
                payment={row.payment}
              />
            ))}
            <MetricsCells label="합계" order={sum.order} payment={sum.payment} total />
          </TableBody>
        </Table>
      </Card>
      <ul className="mt-3 list-disc space-y-1 pl-5 text-sm text-muted-foreground">
        <li>주문: 그날 주문한 주문 수입니다. 만료·취소된 주문도 셉니다.</li>
        <li>
          결제된 주문, 거래액: 그날 결제된 주문의 수와 결제 금액(쿠폰 할인 뒤) 합입니다. 뒤에
          취소·환불돼도 빼지 않습니다.
        </li>
        <li>
          결제 실패율: 그날 결제 승인 시도 중 거절 ÷ (승인 + 거절)입니다. 결과를 아직 모르는 시도는
          미정으로 따로 셉니다.
        </li>
      </ul>
    </>
  );
}

function MetricsCells({
  label,
  order,
  payment,
  total = false,
}: {
  label: string;
  order: OrderDay | null;
  payment: PaymentDay | null;
  total?: boolean;
}) {
  const number = (value: number | undefined) => (value === undefined ? "-" : count.format(value));
  return (
    <TableRow className={total ? "font-semibold" : undefined}>
      <TableCell className="whitespace-nowrap">{label}</TableCell>
      <TableCell className="text-right">{number(order?.orderCount)}</TableCell>
      <TableCell className="text-right">{number(order?.paidOrderCount)}</TableCell>
      <TableCell className="text-right whitespace-nowrap">{formatPrice(order?.gmv)}</TableCell>
      <TableCell className="text-right">{number(payment?.approvedCount)}</TableCell>
      <TableCell className="text-right">{number(payment?.rejectedCount)}</TableCell>
      <TableCell className="text-right">{number(payment?.undecidedCount)}</TableCell>
      <TableCell className="text-right">{formatRate(payment?.failureRate)}</TableCell>
    </TableRow>
  );
}
