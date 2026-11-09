/** 12 lowercase hex characters. Short enough to paste, and not derived from the client. */
export function newExperienceId(randomBytes?: Uint8Array): string {
  const bytes = randomBytes ?? crypto.getRandomValues(new Uint8Array(6));
  if (bytes.length !== 6) throw new Error("id_bytes");
  return [...bytes].map((byte) => byte.toString(16).padStart(2, "0")).join("");
}
