type PlaceholderProps = {
  title: string;
  description: string;
};

/** 아직 화면이 없는 앱의 첫 화면. M1에서 실제 화면이 생기면 지운다. */
export function Placeholder({ title, description }: PlaceholderProps) {
  return (
    <main>
      <h1>{title}</h1>
      <p>{description}</p>
    </main>
  );
}
