// 앱이 함께 쓰는 컴포넌트(ADR 0015). shadcn/ui 방식으로 코드를 여기서 직접 갖는다. 스타일은 theme.css의 토큰을 쓴다.
export { cn } from "./lib/cn";
export { formatDateTime, formatPrice } from "./lib/format";
export { listQuery, PAGE_SIZE } from "./lib/list";
export {
  COUPON_PHASE_LABELS,
  couponPhase,
  couponTerms,
  kstInputToIso,
  toKstInput,
  type CouponPhase,
  type CouponTerms,
} from "./lib/coupon";
export { Button, buttonVariants } from "./button";
export { Input, Textarea } from "./input";
export { Label } from "./label";
export { TextField, TextAreaField } from "./field";
export { Card, CardHeader, CardTitle, CardDescription, CardContent, CardFooter } from "./card";
export { Table, TableHeader, TableBody, TableRow, TableHead, TableCell } from "./table";
export { Badge } from "./badge";
export { Alert } from "./alert";
export { SubmitButton } from "./submit-button";
export { ConfirmSubmit } from "./confirm-submit";
export { ConfirmActionForm } from "./confirm-action-form";
export { StatusTabs } from "./status-tabs";
export { Pager } from "./pager";
export { ProductImage } from "./product-image";
export {
  ProductStatusBadge,
  productStatusLabel,
  PRODUCT_STATUSES,
  PRODUCT_STATUS_LABELS,
  type ProductStatus,
} from "./product-status-badge";
export {
  OrderStatusBadge,
  orderStatusLabel,
  ORDER_STATUS_LABELS,
  type OrderStatus,
} from "./order-status-badge";
export {
  CARRIER_LABELS,
  CLAIM_REASON_LABELS,
  carrierLabel,
  ClaimBadge,
  claimLabel,
  claimReasonLabel,
  faultLabel,
  ProgressBadge,
  progressLabel,
  shipmentStatusLabel,
} from "./fulfillment-badges";
export { ClaimCard, type ClaimSummaryView } from "./claim-card";
export { ShipmentTable, type ShipmentSummaryView } from "./shipment-table";
export { ShippingCard, type ShippingAddress } from "./shipping-card";
export { AccountBar, loginErrorMessage } from "./account-bar";
export { AppShell, PageHeader, type NavItem } from "./app-shell";
export { FormAlert } from "./form-alert";
export {
  idleState,
  fieldErrorsFrom,
  problemState,
  type FormState,
  type Problem,
} from "./form-state";
export { NotificationBell, NotificationList, type NotificationItem } from "./notifications";
