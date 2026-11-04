/** Small, dependency-free field checks. Each returns an error message, or "" when the value is fine. */
export const required = (value: string, label = "This field") => (value.trim() ? "" : `${label} is required.`);

export const email = (value: string) =>
  !value.trim() ? "Email is required." : /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(value.trim()) ? "" : "Enter a valid email address.";

export const password = (value: string) => {
  if (!value) return "Password is required.";
  if (value.length < 8) return "Use at least 8 characters.";
  if (!/[a-z]/.test(value) || !/[A-Z]/.test(value) || !/\d/.test(value)) return "Include an uppercase letter, a lowercase letter and a number.";
  return "";
};

export const minLength = (value: string, n: number, label = "This field") =>
  value.trim().length >= n ? "" : `${label} must be at least ${n} characters.`;

export const dateRange = (from: string, to: string) => {
  if (!from || !to) return "Choose both dates.";
  return to < from ? "The end date can't be before the start date." : "";
};

export const hasErrors = (errors: Record<string, string>) => Object.values(errors).some(Boolean);
