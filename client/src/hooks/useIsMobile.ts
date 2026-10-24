import { useEffect, useState } from 'react';

// Совпадает с брейкпоинтом Tailwind `lg` (1024px): ниже него — мобильная раскладка
const MOBILE_QUERY = '(max-width: 1023px)';

export const useIsMobile = (): boolean => {
  const [isMobile, setIsMobile] = useState<boolean>(
    () => typeof window !== 'undefined' && window.matchMedia(MOBILE_QUERY).matches
  );

  useEffect(() => {
    const mql = window.matchMedia(MOBILE_QUERY);
    const onChange = (e: MediaQueryListEvent) => setIsMobile(e.matches);
    setIsMobile(mql.matches);
    mql.addEventListener('change', onChange);
    return () => mql.removeEventListener('change', onChange);
  }, []);

  return isMobile;
};
