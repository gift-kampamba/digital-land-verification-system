# Manage Profile Workflow - Implementation Complete ✓

## Workflow Overview

The Profile Management Workflow has been successfully implemented with the following steps:

### 1️⃣ User Clicks "Manage Profile"
- **Location**: Officer Dashboard → Sidebar "Manage Profile" link
- **Action**: Opens modal dialog showing current profile information
- **Display**: Read-only view of all profile details:
  - Profile Photo (with fallback initials)
  - Full Name
  - Email Address
  - Phone Number
  - Gender
  - Address

### 2️⃣ System Displays Current Profile Information (Read-Only)
- Profile data is loaded from localStorage session on modal open
- Fields displayed with proper formatting:
  - If photoPath exists: Display saved profile photo from `/uploads/{photoPath}`
  - If no photo: Show initials in a styled avatar circle
  - All text fields show current values or "—" if empty

### 3️⃣ User Clicks "Edit Profile" Button
- Modal switches to edit mode
- All current data is automatically pre-filled in form fields:
  - `updateName`: Pre-filled with current full name
  - `updateGender`: Pre-filled with current gender
  - `updatePhone`: Pre-filled with current phone number
  - `updateAddress`: Pre-filled with current address
  - `updatePhoto`: Ready for new photo upload with preview
  - Password fields: Empty (optional change only)

### 4️⃣ User Modifies Information
- All form fields are editable:
  - **Full Name**: Text input (min 3 characters)
  - **Gender**: Select dropdown (Male/Female/Other)
  - **Phone Number**: Text input (Zambian format supported)
  - **Address**: Textarea for full address
  - **Profile Photo**: File upload with live preview
  - **Password Change** (Optional):
    - Current Password: Required when changing password
    - New Password: Minimum 8 chars, mixed case, numbers

### 5️⃣ User Clicks "Update Profile" Button
- Form validation:
  - At least one field must be updated
  - If changing password, current password is required
  - File uploads are validated for size (5 MB max) and format
- **FormData** sent to backend: `/api/officer/profile/update`
- Button shows "Updating..." state during submission

### 6️⃣ Backend Updates Database
- Backend endpoint: `POST /api/officer/profile/update`
- Request includes:
  - `fullName` (if provided)
  - `gender` (if provided)
  - `phoneNumber` (if provided)
  - `address` (if provided)
  - `photo` (multipart file, if provided)
  - `currentPassword` & `newPassword` (if password change)
- Database updates user record with validated data
- Response includes:
  - Updated profile data
  - Status: `APPROVED` (auto-approved) or `PENDING` (needs approval)

### 7️⃣ User Sees Updated Information
- **If auto-approved**:
  - Success message: "✓ Profile updated successfully and saved to database."
  - Local session updated with new values
  - Profile header refreshed immediately
  - Modal switches back to view mode automatically
  - All subsequent displays show new information

- **If pending approval**:
  - Success message: "✓ Profile update request submitted for approval."
  - Data stored as pending change request
  - "Pending Profile Changes" section shows in modal
  - Once approved by admin, data becomes active

## Technical Implementation

### Frontend Changes

#### 1. Enhanced `auth.js`
```javascript
// New function to update session data
export function updateSession(data) {
  if (data.fullName !== undefined) localStorage.setItem('lv_fullName', data.fullName);
  if (data.phoneNumber !== undefined) localStorage.setItem('lv_phoneNumber', data.phoneNumber);
  if (data.address !== undefined) localStorage.setItem('lv_address', data.address);
  if (data.gender !== undefined) localStorage.setItem('lv_gender', data.gender);
  if (data.photoPath !== undefined) localStorage.setItem('lv_photoPath', data.photoPath);
}

// Enhanced getSession() to include all profile fields
export function getSession() {
  return {
    token:       localStorage.getItem('lv_token'),
    email:       localStorage.getItem('lv_email'),
    name:        localStorage.getItem('lv_name'),
    fullName:    localStorage.getItem('lv_fullName') || localStorage.getItem('lv_name'),
    phoneNumber: localStorage.getItem('lv_phoneNumber'),
    address:     localStorage.getItem('lv_address'),
    gender:      localStorage.getItem('lv_gender'),
    photoPath:   localStorage.getItem('lv_photoPath'),
    role:        localStorage.getItem('lv_role'),
    userId:      localStorage.getItem('lv_userId'),
  };
}
```

#### 2. Officer Dashboard HTML Changes
- **View Mode Section** (Default):
  - Displays current profile information read-only
  - Shows profile photo or initials
  - Has "Edit Profile" and "Close" buttons

- **Edit Mode Section** (Hidden by default):
  - Complete form with all editable fields
  - Pre-filled with current data
  - Has "Cancel" and "Update Profile" buttons

#### 3. JavaScript Functions
- `openProfileModal()`: Opens modal in view mode
- `toggleEditMode()`: Switches between view and edit modes
- `loadProfileData()`: Loads session data and pre-fills form
- `loadProfileHeader()`: Updates dashboard header with current data
- Form submission handler: Sends update to API and refreshes UI

### State Management

**View Mode** ↔ **Edit Mode** Toggle:
```javascript
window.toggleEditMode = function() {
  const viewMode = document.getElementById('viewModeSection');
  const editForm = document.getElementById('profileForm');
  
  // Toggle display
  viewMode.style.display = (viewMode.style.display === 'none') ? 'block' : 'none';
  editForm.style.display = (editForm.style.display === 'none') ? 'block' : 'none';
};
```

### User Experience Features

✅ **Pre-filled Forms**: No data re-entry required
✅ **Live Photo Preview**: See image before uploading
✅ **Form Validation**: Client-side checks before API call
✅ **Loading States**: Button shows "Updating..." during submission
✅ **Success Messages**: Toast notifications for all outcomes
✅ **Auto-refresh**: Dashboard header updates immediately
✅ **Modal Auto-close**: Returns to list view after success
✅ **Pending Changes Display**: Shows items awaiting approval
✅ **Password Protection**: Current password required for changes

## Files Modified

1. **[frontend/js/auth.js](frontend/js/auth.js)**
   - Added `updateSession()` function
   - Enhanced `getSession()` with profile fields
   - Enhanced `saveSession()` to store all fields
   - Enhanced `clearSession()` to clear all fields

2. **[frontend/public/pages/officer/dashboard.html](frontend/public/pages/officer/dashboard.html)**
   - Split profile modal into View Mode and Edit Mode
   - Added `toggleEditMode()` function
   - Enhanced form pre-fill logic
   - Improved form submission handling
   - Added session update on success

## Testing the Workflow

### Test Case 1: View Profile
1. Click "Manage Profile" in sidebar
2. Verify current data displays correctly
3. Verify profile photo shows (or initials if no photo)
4. Click "Close" button

### Test Case 2: Edit Profile
1. Click "Manage Profile"
2. Click "Edit Profile" button
3. Form should appear with pre-filled data
4. Modify at least one field
5. Click "Update Profile"
6. Wait for success message
7. Verify data updated in view mode

### Test Case 3: Upload Photo
1. In edit mode, click "Choose file" under Profile Photo
2. Select an image file
3. Live preview should show selected image
4. Click "Update Profile"
5. Verify photo updated in profile view

### Test Case 4: Change Password
1. In edit mode, enter:
   - Current Password
   - New Password (8+ chars, mixed case, numbers)
2. At least one other field must also be updated
3. Click "Update Profile"
4. Verify next login works with new password

### Test Case 5: Cancel Edit
1. Click "Manage Profile" → "Edit Profile"
2. Modify some fields
3. Click "Cancel"
4. Form should clear and view mode should display
5. Changes should NOT be saved

## API Integration

**Endpoint**: `POST /api/officer/profile/update`

**Request**: FormData
- fullName (string, optional)
- gender (string: MALE|FEMALE|OTHER, optional)
- phoneNumber (string, optional)
- address (string, optional)
- photo (file, optional, max 5MB)
- currentPassword (string, if changing password)
- newPassword (string, if changing password)

**Response**: 
```json
{
  "status": "success",
  "message": "Profile updated successfully",
  "data": {
    "fullName": "John Doe",
    "gender": "MALE",
    "phoneNumber": "+260123456789",
    "address": "Lusaka, Zambia",
    "photoPath": "profile-photos/user123.jpg",
    "status": "APPROVED",
    "updatedAt": "2025-05-07T10:30:00Z"
  }
}
```

---

✅ **Workflow Implementation Status**: COMPLETE

The complete Manage Profile Workflow is now ready for use!
