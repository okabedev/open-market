import { Alert, Badge, Button, Card, CardContent, ConfirmSubmit, PageHeader } from "@openmarket/ui";
import { bff } from "@/lib/bff";
import { requireSession } from "@/lib/session";
import { removeAddress, setDefaultAddress } from "./actions";

/** 배송지는 10개까지다(ACC-3). */
const MAX_ADDRESSES = 10;

export default async function AddressesPage() {
  await requireSession("/my/addresses");
  const member = await bff.serviceClient("member");
  const { data: addresses = [], response } = await member.GET("/v1/members/me/addresses");
  if (!response.ok) {
    return <Alert variant="destructive">배송지를 불러오지 못했습니다.</Alert>;
  }
  const full = addresses.length >= MAX_ADDRESSES;
  return (
    <>
      <PageHeader
        title="배송지"
        description={`기본 배송지가 맨 위에 옵니다. ${MAX_ADDRESSES}개까지 등록할 수 있습니다.`}
        actions={
          full ? null : (
            <Button asChild>
              <a href="/my/addresses/new">배송지 추가</a>
            </Button>
          )
        }
      />
      {addresses.length === 0 ? (
        <Alert>등록한 배송지가 없습니다. 처음 추가한 배송지가 기본 배송지가 됩니다.</Alert>
      ) : (
        <ul className="grid gap-3">
          {addresses.map((address) => (
            <li key={address.addressId}>
              <Card>
                <CardContent className="flex flex-wrap items-start justify-between gap-4">
                  <div className="grid gap-1 text-sm">
                    <p className="flex items-center gap-2 font-medium">
                      {address.label ?? "배송지"}
                      {address.isDefault ? <Badge>기본</Badge> : null}
                    </p>
                    <p>
                      {address.recipientName} · {address.recipientPhone}
                    </p>
                    <p className="text-muted-foreground">
                      ({address.zipCode}) {address.baseAddress} {address.detailAddress}
                    </p>
                  </div>
                  <div className="flex gap-2">
                    {address.isDefault ? null : (
                      <form action={setDefaultAddress}>
                        <input type="hidden" name="addressId" value={address.addressId} />
                        <Button type="submit" variant="outline" size="sm">
                          기본으로
                        </Button>
                      </form>
                    )}
                    <Button asChild variant="outline" size="sm">
                      <a href={`/my/addresses/${address.addressId}/edit`}>수정</a>
                    </Button>
                    <form action={removeAddress}>
                      <input type="hidden" name="addressId" value={address.addressId} />
                      <ConfirmSubmit
                        title="배송지를 지울까요?"
                        description={
                          address.isDefault
                            ? "기본 배송지를 지우면 가장 최근에 추가한 배송지가 기본이 됩니다."
                            : "지운 배송지는 되살릴 수 없습니다."
                        }
                        confirmText="지우기"
                        variant="destructive"
                        size="sm"
                      >
                        삭제
                      </ConfirmSubmit>
                    </form>
                  </div>
                </CardContent>
              </Card>
            </li>
          ))}
        </ul>
      )}
    </>
  );
}
