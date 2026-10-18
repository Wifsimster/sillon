import { Composition } from "remotion";
import { LogoSquare, LogoWordmark } from "./Logo";

export const Root: React.FC = () => (
  <>
    <Composition id="LogoSquare" component={LogoSquare} durationInFrames={90} fps={30} width={1080} height={1080} />
    <Composition id="LogoWordmark" component={LogoWordmark} durationInFrames={120} fps={30} width={1920} height={1080} />
  </>
);
