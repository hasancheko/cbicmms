import {
  BrandRawConfig,
  brandRawConfig,
  customLogoPaths
} from '../config';
import { useLicenseEntitlement } from './useLicenseEntitlement';

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
    name: 'Atlas CMMS',
    shortName: 'Atlas',
    website: 'https://www.atlas-cmms.com',
    mail: 'contact@atlas-cmms.com',
    phone: '+212 6 30 69 00 50',
    addressStreet: '410, Boulevard Zerktouni, Hamad, №1',
    addressCity: 'Casablanca-Morocco 20040'
  };
  const isLicenseValid = useLicenseEntitlement('BRANDING');
  const parsedLogos = parseLogoPaths(customLogoPaths);

  return {
    logo: {
      white: parsedLogos?.white || DEFAULT_WHITE_LOGO,
      dark: parsedLogos?.dark || DEFAULT_DARK_LOGO
    },
    ...(isLicenseValid && brandRawConfig ? brandRawConfig : defaultBrand)
  };
}
