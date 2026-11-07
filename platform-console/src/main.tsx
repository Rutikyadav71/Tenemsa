import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import "@fontsource-variable/dm-sans";
import "@fontsource-variable/outfit";
import "./tokens.css";
import "./ui.css";
import "./console.css";
import App from "./App";

createRoot(document.getElementById("root")!).render(
  <StrictMode><BrowserRouter><App /></BrowserRouter></StrictMode>
);
