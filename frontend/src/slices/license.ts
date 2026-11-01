import type { PayloadAction } from '@reduxjs/toolkit';
import { createSlice } from '@reduxjs/toolkit';
import type { AppThunk } from 'src/store';
import api from '../utils/api';
import { revertAll } from 'src/utils/redux';
import type { LicenseEntitlement, LicensingState } from '../models/owns/license';

const basePath = 'license';

interface LicenseState {
  state: LicensingState;
}

// Self-contained array of all entitlements to avoid missing export errors
const ALL_ENTITLEMENTS: LicenseEntitlement[] = [
  'SSO',
  'WORK_ORDER_HISTORY',
  'WORKFLOW',
  'MULTI_INSTANCE',
  'WEBHOOK',
  'BRANDING',
  'NFC_BARCODE',
  'CUSTOM_ROLES',
  'FILE_ATTACHMENTS',
  'TIME_TRACKING',
  'COST_TRACKING',
  'WORK_ORDER_LINKING',
  'SIGNATURE_CAPTURE',
  'PM_CALENDAR',
  'CONDITION_BASED_PM',
  'ASSET_HIERARCHY',
  'ASSET_DOWNTIME',
  'LOW_STOCK_ALERTS',
  'PARTS_COST_TRACKING',
  'CUSTOMER_VENDOR',
  'FIELD_CONFIGURATION',
  'VOICE_NOTES',
  'ADVANCED_ANALYTICS',
  'API_ACCESS',
  'UNLIMITED_ASSETS',
  'UNLIMITED_LOCATIONS',
  'UNLIMITED_PARTS',
  'UNLIMITED_PM_SCHEDULES',
  'UNLIMITED_ACTIVE_WORK_ORDERS',
  'UNLIMITED_CHECKLIST',
  'UNLIMITED_METERS',
  'RESOURCE_PLANNING'
] as LicenseEntitlement[];

const UNLOCKED_STATE: LicensingState = {
  valid: true,
  entitlements: ALL_ENTITLEMENTS,
  expirationDate: '2099-12-31T23:59:59.000Z',
  planName: 'ENTERPRISE'
};

const initialState: LicenseState = {
  state: UNLOCKED_STATE
};

const slice = createSlice({
  name: 'license',
  initialState,
  reducers: {
    getLicenseValidity(
      state: LicenseState,
      action: PayloadAction<LicenseState['state']>
    ) {
      state.state = {
        ...action.payload,
        ...UNLOCKED_STATE
      };
    }
  }
});

export const reducer = slice.reducer;

export const getLicenseValidity = (): AppThunk => async (dispatch) => {
  try {
    const response = await api.get<LicenseState['state']>(`${basePath}/state`);
    dispatch(slice.actions.getLicenseValidity(response));
  } catch (err) {
    dispatch(slice.actions.getLicenseValidity(UNLOCKED_STATE));
  }
};

export default slice;