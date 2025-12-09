export interface CapUploadCarePlugin {
  echo(options: { value: string }): Promise<{ value: string }>;
}
