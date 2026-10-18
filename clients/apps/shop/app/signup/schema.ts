import { z } from "zod";

/** 가입 입력 규칙. auth의 규칙(PasswordPolicy, EmailAddress)과 같게 두고, 최종 판단은 auth가 한다. */
export const signupSchema = z.object({
  name: z.string().trim().min(1, "이름을 입력해 주세요.").max(50, "이름은 50자까지입니다."),
  email: z
    .string()
    .trim()
    .max(320, "이메일이 너무 깁니다.")
    .pipe(z.email("이메일 형식이 아닙니다.")),
  password: z
    .string()
    .min(10, "비밀번호는 10자 이상입니다.")
    .max(64, "비밀번호는 64자까지입니다.")
    .regex(/\p{L}/u, "비밀번호에 글자를 넣어 주세요.")
    .regex(/\d/, "비밀번호에 숫자를 넣어 주세요."),
});
