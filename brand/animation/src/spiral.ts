export const CENTER = 54;
const OUTER = 31;
const INNER = 15.5;
const TURNS = 2.75;
const START = -Math.PI / 3;
const STEPS = 220;

export const spiralPoints = (): [number, number][] =>
  Array.from({ length: STEPS + 1 }, (_, i) => {
    const t = i / STEPS;
    const a = START + t * TURNS * 2 * Math.PI;
    const r = OUTER - (OUTER - INNER) * t;
    return [CENTER + r * Math.cos(a), CENTER + r * Math.sin(a)];
  });

export const spiralPath = (): string =>
  spiralPoints()
    .map(([x, y], i) => `${i === 0 ? "M" : "L"}${x.toFixed(2)},${y.toFixed(2)}`)
    .join(" ");

export const spiralLength = (): number => {
  const pts = spiralPoints();
  let length = 0;
  for (let i = 1; i < pts.length; i++) {
    length += Math.hypot(pts[i][0] - pts[i - 1][0], pts[i][1] - pts[i - 1][1]);
  }
  return length;
};
