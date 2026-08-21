import { apiClient } from '../api/client';

export interface DownloadOptions {
  url: string;
  params?: Record<string, string | number | undefined>;
  defaultFilename: string;
}

/**
 * Safely extracts human-readable error messages from an Axios error,
 * including when responseType is 'blob' and response.data is a Blob object.
 */
export async function parseBlobErrorMessage(error: any): Promise<string> {
  if (error?.response?.data instanceof Blob) {
    try {
      const text = await error.response.data.text();
      const parsed = JSON.parse(text);
      return parsed.message || parsed.error || text || error.message || 'Download failed';
    } catch {
      return error.message || 'Download failed';
    }
  }
  return error?.response?.data?.message || error?.message || 'Download failed';
}

/**
 * Reusable utility to request and trigger browser file downloads for binary endpoints (PDF, Excel, Zip).
 */
export async function downloadFile(options: DownloadOptions): Promise<void> {
  const { url, params, defaultFilename } = options;
  try {
    const response = await apiClient.get(url, {
      params,
      responseType: 'blob',
    });

    let filename = defaultFilename;
    const disposition = response.headers['content-disposition'];
    if (disposition && disposition.includes('filename=')) {
      const filenameMatch = disposition.match(/filename="?([^";]+)"?/);
      if (filenameMatch && filenameMatch[1]) {
        filename = filenameMatch[1];
      }
    }

    const blobUrl = window.URL.createObjectURL(response.data);
    const link = document.createElement('a');
    link.href = blobUrl;
    link.setAttribute('download', filename);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    window.URL.revokeObjectURL(blobUrl);
  } catch (error: any) {
    const errorMessage = await parseBlobErrorMessage(error);
    throw new Error(errorMessage);
  }
}

export default downloadFile;
