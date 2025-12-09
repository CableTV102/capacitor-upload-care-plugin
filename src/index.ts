import { registerPlugin } from '@capacitor/core';

import type { CapUploadCarePlugin } from './definitions';

const CapUploadCare = registerPlugin<CapUploadCarePlugin>('CapUploadCare', {
  web: () => import('./web').then((m) => new m.CapUploadCareWeb()),
});

export * from './definitions';
export { CapUploadCare };
