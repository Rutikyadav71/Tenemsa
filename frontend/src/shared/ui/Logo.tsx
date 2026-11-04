import type { CSSProperties } from "react";

/**
 * The official Tenemsa logo, never recreated or recoloured.
 * The artwork has dark lettering, so in the dark theme it sits on a light plate (see --logo-plate in tokens.css).
 * `icon` shows only the cloud mark cropped from the same file, for tight spaces.
 */
export function Logo({ height = 36, icon = false, plate = true, style }: { height?: number; icon?: boolean; plate?: boolean; style?: CSSProperties }) {
  const src = icon ? "/brand/tenemsa-icon.png" : "/brand/tenemsa-logo.png";
  return (
    <span className="tn-logo" style={{ height: plate ? undefined : height, ...(plate ? {} : { background: "transparent", padding: 0 }), ...style }}>
      <img src={src} alt="Tenemsa" height={height} style={{ height, width: "auto" }} />
    </span>
  );
}
