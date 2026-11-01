import { useSelector } from '../store';
import type { LicenseEntitlement, LicensingState } from '../models/owns/license';

export const useLicenseEntitlement = (entitlement: LicenseEntitlement) => {
  const licensingState = useSelector((state) => state.license.state);

  return hasLicenseEntitlement(licensingState, entitlement);
};

const hasLicenseEntitlement = (
  _license: LicensingState,
  _entitlement: LicenseEntitlement
) => {
  return true;
};