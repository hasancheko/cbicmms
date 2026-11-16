import {
  BrandRawConfig,
  brandRawConfig,
  customLogoPaths
} from '../config';

const DEFAULT_WHITE_LOGO = '/static/images/logo/logo-white.png';
const DEFAULT_DARK_LOGO = '/static/images/logo/logo.png';

interface BrandConfig extends BrandRawConfig {
  logo: { white: string; dark: string };
}

function parseLogoPaths(raw: unknown): { white?: string; dark?: string } | null {
  if (!raw || raw === 'none' || raw === 'false') return null;
  if (typeof raw === 'object') return raw as { white?: string; dark?: string };
  if (typeof raw === 'string') {
    try {
      return JSON.parse(raw);
    } catch {
      return null;
    }
  }
  return null;
}

export function useBrand(): BrandConfig {
  const defaultBrand: Omit<BrandConfig, 'logo'> = {
    name: 'CBI CMMS',
    shortName: 'CBI',
    website: 'https://www.fmcbi.com',
    mail: 'tesla.tech.iq@gmail.com',
    phone: '',
    addressStreet: '',
    addressCity: 'Baghdad, Iraq'
  };
  const parsedLogos = parseLogoPaths(customLogoPaths);

  return {
    logo: {
      white: parsedLogos?.white || DEFAULT_WHITE_LOGO,
      dark: parsedLogos?.dark || DEFAULT_DARK_LOGO
    },
    ...defaultBrand,
    ...(brandRawConfig || {})
  };
}
