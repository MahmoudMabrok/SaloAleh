export interface GitHubFile {
  text: string;
  sha: string | null;
}

export interface GitHubContents {
  getFile(path: string): Promise<GitHubFile>;
  putFile(
    path: string,
    text: string,
    sha: string | null,
    message: string,
  ): Promise<"ok" | "conflict">;
}

export function encodeUtf8Base64(text: string): string {
  const bytes = new TextEncoder().encode(text);
  let binary = "";
  const chunk = 0x8000;
  for (let i = 0; i < bytes.length; i += chunk) {
    binary += String.fromCharCode(...bytes.subarray(i, i + chunk));
  }
  return btoa(binary);
}

export function decodeUtf8Base64(content: string): string {
  const binary = atob(content.replace(/\s/g, ""));
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
  return new TextDecoder().decode(bytes);
}

export class GitHubContentsClient implements GitHubContents {
  constructor(
    private readonly options: { token: string; repo: string; branch: string },
  ) {}

  async getFile(path: string): Promise<GitHubFile> {
    const response = await this.request("GET", path);
    if (response.status === 404) return { text: "[]", sha: null };
    if (!response.ok) {
      console.error(`github GET ${path} failed: ${response.status}`);
      throw new Error(`github_get_${response.status}`);
    }
    const payload = await response.json();
    if (
      payload.encoding !== "base64" ||
      typeof payload.content !== "string" ||
      typeof payload.sha !== "string"
    ) {
      throw new Error("github_get_shape");
    }
    return { text: decodeUtf8Base64(payload.content), sha: payload.sha };
  }

  async putFile(
    path: string,
    text: string,
    sha: string | null,
    message: string,
  ): Promise<"ok" | "conflict"> {
    const body: Record<string, string> = {
      message,
      content: encodeUtf8Base64(text),
      branch: this.options.branch,
    };
    if (sha) body.sha = sha;
    const response = await this.request("PUT", path, body);
    if (response.status === 200 || response.status === 201) return "ok";
    // 409 is a stale SHA. 422 is the create/update race (file appeared, or a sha
    // was sent for a file that is gone). Both are safe to retry against a fresh read.
    if (response.status === 409 || response.status === 422) return "conflict";
    console.error(`github PUT ${path} failed: ${response.status}`);
    throw new Error(`github_put_${response.status}`);
  }

  private request(method: string, path: string, body?: Record<string, string>): Promise<Response> {
    if (!/^[^/\s]+\/[^/\s]+$/.test(this.options.repo)) {
      throw new Error("github_repo");
    }
    const url =
      `https://api.github.com/repos/${this.options.repo}/contents/${path}?ref=${
        encodeURIComponent(this.options.branch)
      }`;
    return fetch(url, {
      method,
      redirect: "error",
      headers: {
        Accept: "application/vnd.github+json",
        Authorization: `Bearer ${this.options.token}`,
        "X-GitHub-Api-Version": "2022-11-28",
        "User-Agent": "SaloAlehCommunityApi",
        "Content-Type": "application/json",
      },
      body: body ? JSON.stringify(body) : undefined,
    });
  }
}
