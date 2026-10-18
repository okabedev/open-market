-- 입점 신청 취소 상태의 철자를 프로젝트 관례(CANCELLED, glossary)에 맞춘다. V3까지 CANCELED로 저장했다.
UPDATE seller_application SET status = 'CANCELLED' WHERE status = 'CANCELED';
