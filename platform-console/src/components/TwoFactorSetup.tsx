import { useEffect, useState } from "react";
import QRCode from "qrcode";

/** Shows the secret as a QR code and as text, for an authenticator app (Google Authenticator, Authy, 1Password...). */
export default function TwoFactorSetup({ uri, secret }: { uri: string; secret: string }) {
  const [img, setImg] = useState<string | null>(null);
  useEffect(() => { QRCode.toDataURL(uri, { width: 200, margin: 1 }).then(setImg).catch(() => setImg(null)); }, [uri]);
  return (
    <div className="pc-2fa">
      <p>Scan this code with an authenticator app, or type the key in by hand.</p>
      <div className="pc-2fa-row">
        {img ? <img src={img} width={160} height={160} alt="QR code for your authenticator app" className="pc-qr" /> : <div className="pc-qr pc-skel" style={{ width: 160, height: 160 }} />}
        <div>
          <div className="pc-key-label">Setup key</div>
          <code className="pc-key">{secret.replace(/(.{4})/g, "$1 ").trim()}</code>
          <p className="tn-hint" style={{ marginTop: 8 }}>Time-based, 6 digits, 30 seconds.</p>
        </div>
      </div>
    </div>
  );
}
