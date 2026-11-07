/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Base URL of the patient portal app. Defaults to http://localhost:3001. */
  readonly VITE_PATIENT_APP_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
