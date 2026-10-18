import {
  AbsoluteFill,
  Easing,
  interpolate,
  spring,
  staticFile,
  useCurrentFrame,
  useVideoConfig,
} from "remotion";
import { CENTER, spiralLength, spiralPath } from "./spiral";

const VINYL = "#15120F";
const SLEEVE = "#F6EFE4";
const LABEL = "#E8A33D";
const MUTED = "#968A7A";

const fontFaces = `
@font-face { font-family: "Fraunces"; src: url("${staticFile("fraunces_semibold.ttf")}"); font-weight: 600; }
@font-face { font-family: "DM Sans"; src: url("${staticFile("dmsans_regular.ttf")}"); font-weight: 400; }
`;

const PATH = spiralPath();
const LENGTH = spiralLength();

const Mark: React.FC<{ size: number }> = ({ size }) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();

  const label = spring({ frame, fps, config: { damping: 12, stiffness: 120 } });
  const draw = interpolate(frame, [8, 52], [0, 1], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
    easing: Easing.inOut(Easing.cubic),
  });
  const hole = spring({ frame: frame - 50, fps, config: { damping: 14 } });
  const spin = interpolate(frame, [0, 52, 120], [-140, 0, 40], {
    extrapolateRight: "extend",
    easing: Easing.out(Easing.quad),
  });

  return (
    <svg width={size} height={size} viewBox="0 0 108 108">
      <g transform={`rotate(${spin} ${CENTER} ${CENTER})`}>
        <path
          d={PATH}
          fill="none"
          stroke={SLEEVE}
          strokeWidth={2.6}
          strokeLinecap="round"
          strokeLinejoin="round"
          strokeDasharray={LENGTH}
          strokeDashoffset={LENGTH * (1 - draw)}
          opacity={draw > 0.001 ? 1 : 0}
        />
      </g>
      <circle cx={CENTER} cy={CENTER} r={11 * label} fill={LABEL} />
      <circle cx={CENTER} cy={CENTER} r={2.4 * hole} fill={VINYL} />
    </svg>
  );
};

export const LogoSquare: React.FC = () => (
  <AbsoluteFill style={{ backgroundColor: VINYL, alignItems: "center", justifyContent: "center" }}>
    <Mark size={1000} />
  </AbsoluteFill>
);

export const LogoWordmark: React.FC = () => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const word = spring({ frame: frame - 48, fps, config: { damping: 18 } });
  const tagline = interpolate(frame, [72, 92], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp" });

  return (
    <AbsoluteFill style={{ backgroundColor: VINYL, alignItems: "center", justifyContent: "center" }}>
      <style>{fontFaces}</style>
      <div style={{ display: "flex", alignItems: "center", gap: 40 }}>
        <Mark size={560} />
        <div style={{ display: "flex", flexDirection: "column" }}>
          <div
            style={{
              fontFamily: "Fraunces",
              fontWeight: 600,
              fontSize: 260,
              color: SLEEVE,
              lineHeight: 1,
              opacity: word,
              transform: `translateX(${(1 - word) * -60}px)`,
            }}
          >
            Sillon
          </div>
          <div style={{ fontFamily: "DM Sans", fontSize: 54, color: MUTED, marginTop: 24, opacity: tagline }}>
            Ta musique, ton serveur, un seul sillon.
          </div>
        </div>
      </div>
    </AbsoluteFill>
  );
};
