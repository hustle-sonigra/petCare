export function downloadFile(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  // Allow the browser to start the download before releasing the temporary URL.
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
