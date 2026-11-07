import { useEffect } from 'react';

/** Sets document.title for the current page. */
export function useDocumentTitle(title: string) {
  useEffect(() => {
    document.title = title ? `${title} · CareHMS` : 'CareHMS';
  }, [title]);
}
