import { useEffect } from 'react';

/** Sets document.title for the current page (suffixes the app name once). */
export function useDocumentTitle(title: string) {
  useEffect(() => {
    document.title = title ? `${title} · CareHMS` : 'CareHMS';
  }, [title]);
}
