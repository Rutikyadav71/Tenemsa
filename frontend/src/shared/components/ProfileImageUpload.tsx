import { useRef, useState, useCallback, useEffect, type ChangeEvent, type MouseEvent as ReactMouseEvent, type TouchEvent as ReactTouchEvent, type WheelEvent as ReactWheelEvent } from "react";
import axiosInstance from "../api/axiosInstance";

interface ImageCropperModalProps {
  imageDataUrl: string;
  onSave: (file: File) => void;
  onClose: () => void;
}

const CameraIcon = () => <svg width="14" height="14" fill="none" viewBox="0 0 24 24"><path d="M23 19a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h4l2-3h6l2 3h4a2 2 0 0 1 2 2z" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" /><circle cx="12" cy="13" r="4" stroke="currentColor" strokeWidth="1.8" /></svg>;
const ZoomInIcon = () => <svg width="14" height="14" fill="none" viewBox="0 0 24 24"><circle cx="11" cy="11" r="8" stroke="currentColor" strokeWidth="1.8" /><path d="M21 21l-4.35-4.35M11 8v6M8 11h6" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" /></svg>;
const ZoomOutIcon = () => <svg width="14" height="14" fill="none" viewBox="0 0 24 24"><circle cx="11" cy="11" r="8" stroke="currentColor" strokeWidth="1.8" /><path d="M21 21l-4.35-4.35M8 11h6" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" /></svg>;
const RotateIcon = () => <svg width="14" height="14" fill="none" viewBox="0 0 24 24"><path d="M23 4v6h-6" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" /><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" /></svg>;
const CloseIcon = () => <svg width="13" height="13" fill="none" viewBox="0 0 24 24"><path d="M18 6L6 18M6 6l12 12" stroke="currentColor" strokeWidth="2" strokeLinecap="round" /></svg>;
const UploadIcon = () => <svg width="14" height="14" fill="none" viewBox="0 0 24 24"><polyline points="16 16 12 12 8 16" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" /><line x1="12" y1="12" x2="12" y2="21" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" /><path d="M20.39 18.39A5 5 0 0 0 18 9h-1.26A8 8 0 1 0 3 16.3" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" /></svg>;

function ImageCropperModal({ imageDataUrl, onSave, onClose }: ImageCropperModalProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const imgRef = useRef(new Image());
  const [scale, setScale] = useState(1);
  const [rotation, setRotation] = useState(0);
  const [offset, setOffset] = useState({ x: 0, y: 0 });
  const [dragging, setDragging] = useState(false);
  const [dragStart, setDragStart] = useState({ x: 0, y: 0 });
  const [saving, setSaving] = useState(false);
  const SIZE = 300;

  const draw = useCallback(() => {
    const canvas = canvasRef.current;
    if (!canvas || !imgRef.current.complete) return;
    const ctx = canvas.getContext("2d");
    if (!ctx) return;
    ctx.clearRect(0, 0, SIZE, SIZE);
    ctx.fillStyle = "#07090f";
    ctx.fillRect(0, 0, SIZE, SIZE);
    ctx.save();
    ctx.beginPath();
    ctx.arc(SIZE / 2, SIZE / 2, SIZE / 2 - 2, 0, Math.PI * 2);
    ctx.clip();
    ctx.translate(SIZE / 2 + offset.x, SIZE / 2 + offset.y);
    ctx.rotate((rotation * Math.PI) / 180);
    ctx.scale(scale, scale);
    const img = imgRef.current;
    const ratio = Math.max(SIZE / img.naturalWidth, SIZE / img.naturalHeight);
    const w = img.naturalWidth * ratio, h = img.naturalHeight * ratio;
    ctx.drawImage(img, -w / 2, -h / 2, w, h);
    ctx.restore();
    ctx.beginPath();
    ctx.arc(SIZE / 2, SIZE / 2, SIZE / 2 - 2, 0, Math.PI * 2);
    ctx.strokeStyle = "rgba(99,102,241,0.85)";
    ctx.lineWidth = 2.5;
    ctx.stroke();
  }, [scale, rotation, offset]);

  useEffect(() => { imgRef.current.onload = draw; imgRef.current.src = imageDataUrl; return () => { imgRef.current.onload = null; }; }, [imageDataUrl]);
  useEffect(() => { draw(); }, [draw]);

  const onMD = (e: ReactMouseEvent<HTMLCanvasElement>) => { setDragging(true); setDragStart({ x: e.clientX - offset.x, y: e.clientY - offset.y }); };
  const onMM = (e: ReactMouseEvent<HTMLCanvasElement>) => { if (dragging) setOffset({ x: e.clientX - dragStart.x, y: e.clientY - dragStart.y }); };
  const onMU = () => setDragging(false);
  const onTS = (e: ReactTouchEvent<HTMLCanvasElement>) => { const t = e.touches[0]; setDragging(true); setDragStart({ x: t.clientX - offset.x, y: t.clientY - offset.y }); };
  const onTM = (e: ReactTouchEvent<HTMLCanvasElement>) => { if (dragging) { const t = e.touches[0]; setOffset({ x: t.clientX - dragStart.x, y: t.clientY - dragStart.y }); } };
  const onWh = (e: ReactWheelEvent<HTMLCanvasElement>) => { e.preventDefault(); setScale(s => Math.min(3, Math.max(0.5, s - e.deltaY * 0.001))); };

  const handleSave = () => {
    setSaving(true);
    canvasRef.current?.toBlob(blob => {
      if (!blob) { setSaving(false); return; }
      onSave(new File([blob], "profile.jpg", { type: "image/jpeg" }));
    }, "image/jpeg", 0.92);
  };

  return (
    <div style={{
      position: "fixed", inset: 0, background: "rgba(0,0,0,0.9)", backdropFilter: "blur(8px)",
      display: "flex", alignItems: "center", justifyContent: "center", zIndex: 9999, animation: "fadeIn .18s ease"
    }}>
      <div style={{
        background: "var(--bg-elevated)", border: "1px solid var(--border-bright)",
        borderRadius: 20, padding: 28, width: 390, maxWidth: "95vw", boxShadow: "0 24px 80px rgba(0,0,0,0.8)"
      }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 18 }}>
          <div>
            <div style={{ fontSize: 17, fontWeight: 700, color: "var(--text-1)", fontFamily: "'Outfit',sans-serif" }}>Adjust Photo</div>
            <div style={{ fontSize: 11, color: "var(--text-3)", marginTop: 2 }}>Drag · Scroll/pinch to zoom</div>
          </div>
          <button onClick={onClose} style={{ background: "none", border: "none", color: "var(--text-3)", cursor: "pointer", padding: 6, borderRadius: 6, display: "flex" }}><CloseIcon /></button>
        </div>

        <div style={{ display: "flex", justifyContent: "center", marginBottom: 20 }}>
          <canvas ref={canvasRef} width={SIZE} height={SIZE}
            style={{
              borderRadius: "50%", cursor: dragging ? "grabbing" : "grab",
              display: "block", touchAction: "none", userSelect: "none",
              boxShadow: "0 0 0 4px rgba(99,102,241,0.25)"
            }}
            onMouseDown={onMD} onMouseMove={onMM} onMouseUp={onMU} onMouseLeave={onMU}
            onTouchStart={onTS} onTouchMove={onTM} onTouchEnd={onMU}
            onWheel={onWh} />
        </div>

        {[
          {
            label: "Zoom", val: `${Math.round(scale * 100)}%`,
            slider: { min: 50, max: 300, value: Math.round(scale * 100), onChange: (e: ChangeEvent<HTMLInputElement>) => setScale(Number(e.target.value) / 100) },
            decBtn: () => setScale(s => Math.max(0.5, s - 0.1)), incBtn: () => setScale(s => Math.min(3, s + 0.1)),
            decIcon: <ZoomOutIcon />, incIcon: <ZoomInIcon />
          },
          {
            label: "Rotate", val: `${rotation}°`,
            slider: { min: -180, max: 180, value: rotation, onChange: (e: ChangeEvent<HTMLInputElement>) => setRotation(Number(e.target.value)) },
            decBtn: () => setRotation(r => r - 90), incBtn: () => setRotation(r => r + 90),
            decIcon: <RotateIcon />, incIcon: <div style={{ transform: "scaleX(-1)", display: "flex" }}><RotateIcon /></div>
          },
        ].map(ctrl => (
          <div key={ctrl.label} style={{ marginBottom: 14 }}>
            <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 5 }}>
              <span style={{ fontSize: 11, color: "var(--text-3)", textTransform: "uppercase", letterSpacing: "0.5px" }}>{ctrl.label}</span>
              <span style={{ fontSize: 11, fontWeight: 600, color: "var(--accent-light)" }}>{ctrl.val}</span>
            </div>
            <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
              <button onClick={ctrl.decBtn} style={{
                background: "var(--bg-hover)", border: "1px solid var(--border)",
                borderRadius: 6, padding: "5px 7px", cursor: "pointer", color: "var(--text-2)", display: "flex"
              }}>{ctrl.decIcon}</button>
              <input type="range" {...ctrl.slider} style={{ flex: 1, accentColor: "var(--accent)", cursor: "pointer" }} />
              <button onClick={ctrl.incBtn} style={{
                background: "var(--bg-hover)", border: "1px solid var(--border)",
                borderRadius: 6, padding: "5px 7px", cursor: "pointer", color: "var(--text-2)", display: "flex"
              }}>{ctrl.incIcon}</button>
            </div>
          </div>
        ))}

        <div style={{ display: "flex", gap: 10, marginTop: 20 }}>
          <button onClick={() => { setScale(1); setRotation(0); setOffset({ x: 0, y: 0 }); }}
            style={{
              padding: "10px 16px", background: "var(--bg-hover)", border: "1px solid var(--border-bright)",
              color: "var(--text-2)", borderRadius: 8, cursor: "pointer", fontSize: 13, fontWeight: 500
            }}>Reset</button>
          <button onClick={handleSave} disabled={saving}
            style={{
              flex: 1, background: "var(--accent)", color: "white", border: "none", padding: "10px 16px",
              borderRadius: 8, cursor: saving ? "not-allowed" : "pointer", fontSize: 13, fontWeight: 600,
              display: "flex", alignItems: "center", justifyContent: "center", gap: 8, opacity: saving ? 0.7 : 1
            }}>
            {saving ? <><div style={{
              width: 14, height: 14, border: "2px solid rgba(255,255,255,0.3)",
              borderTopColor: "white", borderRadius: "50%", animation: "spin .7s linear infinite"
            }} /> Uploading...</>
              : <><UploadIcon /> Save Photo</>}
          </button>
        </div>
      </div>
      <style>{`@keyframes fadeIn{from{opacity:0;transform:scale(.97)}to{opacity:1;transform:scale(1)}}@keyframes spin{to{transform:rotate(360deg)}}`}</style>
    </div>
  );
}

const AV_COLORS = ["#6366f1", "#2dd4bf", "#22c55e", "#f59e0b", "#ef4444", "#a78bfa"];
const getColor = (n?: string | null) => AV_COLORS[(n?.charCodeAt(0) || 0) % AV_COLORS.length];

interface ProfileImageUploadProps {
  empId: number | string;
  currentUrl?: string | null;
  name?: string | null;
  onUpload?: (url: string) => void;
  size?: number;
  editable?: boolean;
  customUploadFn?: (file: File) => Promise<string>;
}

/**
 * ProfileImageUpload
 * Props:
 *   empId          — employee's empId (used for default upload URL)
 *   currentUrl     — existing profile image URL
 *   name           — display name (initials fallback)
 *   onUpload(url)  — callback with new URL after upload
 *   size           — avatar size px (default 80)
 *   editable       — show camera button (default true)
 *   customUploadFn — optional async fn(File) => string (url). Overrides default upload.
 *                    Used by AdminProfile to hit /api/upload/admin-profile/{id}.
 */
export default function ProfileImageUpload({ empId, currentUrl, name, onUpload, size = 80, editable = true, customUploadFn }: ProfileImageUploadProps) {
  const [preview, setPreview] = useState<string | null>(currentUrl || null);
  const [cropSrc, setCropSrc] = useState<string | null>(null);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState("");
  const inputRef = useRef<HTMLInputElement>(null);
  const color = getColor(name);
  const initials = name?.split(" ").map(n => n[0]).join("").toUpperCase().slice(0, 2) || "?";

  const handleFileSelect = (e: ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    e.target.value = "";
    if (!["image/jpeg", "image/jpg", "image/png", "image/webp"].includes(file.type)) {
      setError("Only JPG, PNG, WEBP allowed."); return;
    }
    setError("");
    const reader = new FileReader();
    reader.onload = ev => setCropSrc(typeof ev.target?.result === "string" ? ev.target.result : null);
    reader.readAsDataURL(file);
  };

  const handleCropSave = async (croppedFile: File) => {
    setUploading(true);
    try {
      let url: string;
      if (customUploadFn) {
        // Admin or custom upload path
        url = await customUploadFn(croppedFile);
      } else {
        // Employee default: POST /api/upload/profile/{empId}
        const fd = new FormData();
        fd.append("file", croppedFile, "profile.jpg");
        const res = await axiosInstance.post(`/api/upload/profile/${empId}`, fd);
        url = res.data.url;
      }
      setPreview(url);
      setCropSrc(null);
      setError("");
      onUpload?.(url);
      if (String(empId) === String(localStorage.getItem("empId"))) {
        localStorage.setItem("profileImageUrl", url);
      }
    } catch (err: any) {
      const msg = err.response?.data;
      setError(typeof msg === "string" ? msg : "Upload failed. Check Cloudinary config.");
      setCropSrc(null);
    } finally { setUploading(false); }
  };

  return (
    <div style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 8 }}>
      <div style={{ position: "relative", display: "inline-block" }}>
        {preview
          ? <img src={preview} alt="Profile"
            style={{
              width: size, height: size, borderRadius: "50%", objectFit: "cover",
              border: "3px solid var(--bg-surface)", display: "block", opacity: uploading ? .6 : 1
            }} />
          : <div style={{
            width: size, height: size, borderRadius: "50%", background: `${color}22`, color,
            border: "3px solid var(--bg-surface)", display: "flex", alignItems: "center",
            justifyContent: "center", fontFamily: "'Outfit',sans-serif", fontSize: size * .3, fontWeight: 700
          }}>
            {initials}
          </div>
        }
        {uploading && (
          <div style={{
            position: "absolute", inset: 0, borderRadius: "50%", background: "rgba(0,0,0,0.55)",
            display: "flex", alignItems: "center", justifyContent: "center"
          }}>
            <div style={{
              width: 22, height: 22, border: "2px solid rgba(255,255,255,0.3)",
              borderTopColor: "white", borderRadius: "50%", animation: "spin .7s linear infinite"
            }} />
          </div>
        )}
        {editable && !uploading && (
          <button onClick={() => inputRef.current?.click()} title="Change photo"
            style={{
              position: "absolute", bottom: -2, right: -2, width: 28, height: 28, borderRadius: "50%",
              background: "var(--accent)", color: "white", border: "2px solid var(--bg-surface)",
              display: "flex", alignItems: "center", justifyContent: "center", cursor: "pointer"
            }}>
            <CameraIcon />
          </button>
        )}
      </div>
      <input ref={inputRef} type="file" accept="image/jpeg,image/jpg,image/png,image/webp"
        style={{ display: "none" }} onChange={handleFileSelect} />
      {editable && !uploading && <div style={{ fontSize: 11, color: "var(--text-3)", textAlign: "center" }}>Click camera · JPG PNG WEBP · max 2 MB</div>}
      {error && <div style={{
        fontSize: 12, color: "var(--red)", padding: "6px 12px", background: "var(--red-glow)",
        borderRadius: 6, border: "1px solid rgba(239,68,68,0.3)", maxWidth: 260, textAlign: "center", lineHeight: 1.5
      }}>{error}</div>}
      {cropSrc && <ImageCropperModal imageDataUrl={cropSrc} onSave={handleCropSave} onClose={() => setCropSrc(null)} />}
      <style>{`@keyframes spin{to{transform:rotate(360deg)}}`}</style>
    </div>
  );
}
