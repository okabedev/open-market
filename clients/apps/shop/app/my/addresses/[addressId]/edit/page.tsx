import { Alert, Card, CardContent, CardHeader, CardTitle } from "@openmarket/ui";
import { bff } from "@/lib/bff";
import { requireSession } from "@/lib/session";
import { changeAddress } from "../../actions";
import { AddressForm } from "../../address-form";

export default async function EditAddressPage({
  params,
}: PageProps<"/my/addresses/[addressId]/edit">) {
  const { addressId } = await params;
  await requireSession(`/my/addresses/${addressId}/edit`);
  const member = await bff.serviceClient("member");
  // 배송지는 10개까지라 단건 조회 없이 목록에서 찾는다.
  const { data: addresses = [] } = await member.GET("/v1/members/me/addresses");
  const address = addresses.find((candidate) => candidate.addressId === addressId);
  if (!address) {
    return <Alert variant="destructive">배송지를 찾을 수 없습니다.</Alert>;
  }
  return (
    <Card>
      <CardHeader>
        <CardTitle>배송지 수정</CardTitle>
      </CardHeader>
      <CardContent>
        <AddressForm
          action={changeAddress}
          hidden={{ addressId }}
          submitText="저장"
          initial={{
            label: address.label ?? "",
            recipientName: address.recipientName ?? "",
            recipientPhone: address.recipientPhone ?? "",
            zipCode: address.zipCode ?? "",
            baseAddress: address.baseAddress ?? "",
            detailAddress: address.detailAddress ?? "",
          }}
        />
      </CardContent>
    </Card>
  );
}
