import { Card, CardContent, CardHeader, CardTitle } from "@openmarket/ui";
import { randomUUID } from "node:crypto";
import { requireSession } from "@/lib/session";
import { addAddress } from "../actions";
import { AddressForm } from "../address-form";

export default async function NewAddressPage() {
  await requireSession("/my/addresses/new");
  return (
    <Card>
      <CardHeader>
        <CardTitle>배송지 추가</CardTitle>
      </CardHeader>
      <CardContent>
        <AddressForm
          action={addAddress}
          // 화면을 그릴 때 한 번 만든다. 같은 화면에서 두 번 제출해도 배송지는 하나만 생긴다.
          hidden={{ idempotencyKey: randomUUID() }}
          askDefault
          submitText="추가"
          initial={{
            label: "",
            recipientName: "",
            recipientPhone: "",
            zipCode: "",
            baseAddress: "",
            detailAddress: "",
          }}
        />
      </CardContent>
    </Card>
  );
}
