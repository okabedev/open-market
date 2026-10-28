// 관리자 2단계 인증 코드(RFC 6238, HMAC-SHA1, 6자리, 30초). auth와 같은 계산이다(docs/architecture/auth.md "관리자 2단계 인증").
import crypto from "k6/crypto";

const ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

function base32Decode(text) {
  const bytes = [];
  let buffer = 0;
  let bits = 0;
  for (const c of text.replace(/[\s=]/g, "").toUpperCase()) {
    const value = ALPHABET.indexOf(c);
    if (value < 0) {
      throw new Error("Base32 문자가 아니다");
    }
    buffer = (buffer << 5) | value;
    bits += 5;
    if (bits >= 8) {
      bytes.push((buffer >> (bits - 8)) & 0xff);
      bits -= 8;
    }
  }
  return new Uint8Array(bytes).buffer;
}

/** 지금 시각의 30초 단계. */
export function currentStep() {
  return Math.floor(Date.now() / 1000 / 30);
}

/** 그 단계의 6자리 코드. */
export function totpCode(secretBase32, step) {
  const counter = new Uint8Array(8);
  let value = step;
  for (let i = 7; i >= 0; i--) {
    counter[i] = value & 0xff;
    value = Math.floor(value / 256);
  }
  const hex = crypto.hmac("sha1", base32Decode(secretBase32), counter.buffer, "hex");
  const hash = [];
  for (let i = 0; i < hex.length; i += 2) {
    hash.push(parseInt(hex.substring(i, i + 2), 16));
  }
  const offset = hash[hash.length - 1] & 0x0f;
  const binary =
    ((hash[offset] & 0x7f) << 24) | (hash[offset + 1] << 16) | (hash[offset + 2] << 8) | hash[offset + 3];
  return String(binary % 1000000).padStart(6, "0");
}
