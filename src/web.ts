import { WebPlugin } from '@capacitor/core';

import type { CapUploadCarePlugin } from './definitions';

export class CapUploadCareWeb extends WebPlugin implements CapUploadCarePlugin {
  async echo(options: { value: string }): Promise<{ value: string }> {
    console.log('ECHO', options);
    return options;
  }
}
