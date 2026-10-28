import { execFileSync } from "node:child_process";
import { randomUUID } from "node:crypto";
import { env } from "./env";

/**
 * 헤더는 맞고 페이로드가 JSON이 아닌 이벤트를 토픽에 넣고 이벤트 ID(`id` 헤더)를 돌려준다. 리스너는 처리하는 `type`만 읽고 나머지는
 * 넘기므로 `type`은 구독자가 처리하는 이벤트여야 한다. 페이로드를 읽지 못하면 영구 오류라 재시도 없이 그 이벤트를 처리하는 그룹마다 DLT로
 * 가고, 서비스의 DLT 수집기가 `dead_letter`에 모은다(docs/architecture/messaging.md "DLT 재처리"). E2E 스택의 Kafka 컨테이너에서 콘솔
 * 프로듀서로 보낸다.
 */
export function produceUnreadableEvent(topic: string, type: string, key: string): string {
  const eventId = randomUUID();
  // 형식: <헤더>\t<키>\t<값>. 헤더는 `이름=값`을 쉼표로 잇는다(시각에 `:`가 있어 이름과 값 구분자를 `=`로 바꾼다).
  const headers = `type=${type},id=${eventId},occurredAt=${new Date().toISOString()},schemaVersion=1`;
  const line = `${headers}\t${key}\tnot-json\n`;
  execFileSync(
    "docker",
    [
      "compose",
      "-p",
      env.composeProject,
      "exec",
      "-T",
      "kafka",
      "/opt/kafka/bin/kafka-console-producer.sh",
      "--bootstrap-server",
      "kafka:29092",
      "--topic",
      topic,
      "--reader-property",
      "parse.key=true",
      "--reader-property",
      "parse.headers=true",
      "--reader-property",
      "headers.key.separator==",
    ],
    { input: line, stdio: ["pipe", "ignore", "inherit"] },
  );
  return eventId;
}
