# UI Changes Guide - Noise Profile Feature

## Toolbar Changes

### Before
```
┌─────────────────────────────────────────────────────────┐
│  [Hidden tap area]          [Settings] Answer Mode      │
└─────────────────────────────────────────────────────────┘
```

### After
```
┌─────────────────────────────────────────────────────────┐
│  [Hidden tap area]  [Mic] Loud Truck  [Settings] Long   │
└─────────────────────────────────────────────────────────┘
```

**Changes:**
- Added noise profile button with microphone icon (yellow/amber)
- Shows current profile label (e.g., "Loud Truck")
- Positioned left of Answer Mode button
- Compact design to fit in toolbar

## Noise Profile Button

### Visual Design
```
┌──────────────────────────┐
│ 🎤 Loud Truck            │  ← Yellow microphone icon
└──────────────────────────┘
```

**Colors:**
- Background: Dark gray (#2D2D2D)
- Text: White
- Icon: Yellow/Amber (#FFC107)
- Border: None (unless selected in dialog)

**Size:**
- Height: 32dp (compact)
- Padding: 8dp horizontal, 6dp vertical
- Font: 12sp (small, fits toolbar)

**Interaction:**
- Tap to open Noise Profile Dialog
- Shows current profile name
- Updates immediately when profile changes

## Noise Profile Dialog

### Dialog Layout
```
┌─────────────────────────────────────────────────────────┐
│                    Noise Profile                        │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  Select the noise level for your current driving       │
│  environment:                                          │
│                                                         │
│  ┌─────────────────────────────────────────────────┐   │
│  │ Quiet                                       🎤   │   │
│  │ Office/home environment. Shorter silence       │   │
│  │ timeout (800ms).                              │   │
│  └─────────────────────────────────────────────────┘   │
│                                                         │
│  ┌─────────────────────────────────────────────────┐   │
│  │ Normal                                          │   │
│  │ Regular city/suburban driving. Standard         │   │
│  │ timeout (1000ms).                              │   │
│  └─────────────────────────────────────────────────┘   │
│                                                         │
│  ┌─────────────────────────────────────────────────┐   │
│  │ Loud Truck                                  🎤   │   │
│  │ Highway/high noise. Longer timeout (1500ms)    │   │
│  │ to capture full speech.                        │   │
│  └─────────────────────────────────────────────────┘   │
│                                                         │
│                          [Close]                       │
└─────────────────────────────────────────────────────────┘
```

### Profile Option Styling

**Unselected:**
```
┌─────────────────────────────────────────────────┐
│ Quiet                                           │
│ Office/home environment. Shorter silence        │
│ timeout (800ms).                                │
└─────────────────────────────────────────────────┘
```
- Background: Dark gray (#2D2D2D)
- Text: White
- Border: None
- Icon: Hidden

**Selected:**
```
┌─────────────────────────────────────────────────┐
│ Loud Truck                                  🎤   │
│ Highway/high noise. Longer timeout (1500ms)    │
│ to capture full speech.                        │
└─────────────────────────────────────────────────┘
```
- Background: Yellow with 20% opacity (#FFC107 + alpha)
- Text: Yellow (#FFC107)
- Border: 2dp yellow border
- Icon: Yellow microphone (visible)

### Dialog Colors
- Title: White
- Description text: Gray
- Background: Dark (#1A1A1A)
- Option background (unselected): Dark gray (#2D2D2D)
- Option background (selected): Yellow with opacity (#FFC107 + 20%)
- Option border (selected): Yellow (#FFC107)
- Icon (selected): Yellow (#FFC107)

## User Interaction Flow

### Step 1: Initial State
```
┌─────────────────────────────────────────────────────────┐
│  [Hidden tap area]  [Mic] Loud Truck  [Settings] Long   │
│                                                         │
│                    LISTENING...                         │
│                                                         │
│                  [Large green circle]                   │
│                                                         │
│                    [Microphone icon]                    │
└─────────────────────────────────────────────────────────┘
```

### Step 2: User Taps Noise Profile Button
```
┌─────────────────────────────────────────────────────────┐
│  [Hidden tap area]  [Mic] Loud Truck  [Settings] Long   │
│                                                         │
│  ┌─────────────────────────────────────────────────┐   │
│  │         Noise Profile Dialog Opens              │   │
│  │                                                 │   │
│  │  [Quiet option]                                 │   │
│  │  [Normal option]                                │   │
│  │  [Loud Truck option] ← Selected (yellow)        │   │
│  │                                                 │   │
│  │                    [Close]                      │   │
│  └─────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────┘
```

### Step 3: User Selects Different Profile
```
┌─────────────────────────────────────────────────────────┐
│  [Hidden tap area]  [Mic] Normal  [Settings] Long       │
│                                                         │
│  ┌─────────────────────────────────────────────────┐   │
│  │         Noise Profile Dialog                    │   │
│  │                                                 │   │
│  │  [Quiet option]                                 │   │
│  │  [Normal option] ← Selected (yellow)            │   │
│  │  [Loud Truck option]                            │   │
│  │                                                 │   │
│  │                    [Close]                      │   │
│  └─────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────┘
```

### Step 4: User Closes Dialog
```
┌─────────────────────────────────────────────────────────┐
│  [Hidden tap area]  [Mic] Normal  [Settings] Long       │
│                                                         │
│                    LISTENING...                         │
│                                                         │
│                  [Large green circle]                   │
│                                                         │
│                    [Microphone icon]                    │
│                                                         │
│  Profile changed to: Normal                            │
└─────────────────────────────────────────────────────────┘
```

## Responsive Design

### Toolbar Layout
```
┌─────────────────────────────────────────────────────────┐
│ [Flex: 1]  [Button 1]  [Button 2]                       │
│ (tap area) (Noise)     (Answer Mode)                    │
└─────────────────────────────────────────────────────────┘
```

**Spacing:**
- Buttons: 4dp padding between them
- Toolbar height: 48dp
- Button height: 32dp (centered vertically)

### Dialog Responsiveness
- Dialog width: Fills screen with 32dp padding
- Option height: Auto (content-based)
- Scrollable if content exceeds screen height

## Accessibility

### Visual Indicators
- ✓ Yellow/amber color for noise profile (distinct from green answer mode)
- ✓ Microphone icon for audio-related feature
- ✓ Clear text labels
- ✓ High contrast (white text on dark background)

### Touch Targets
- ✓ Buttons: 32dp minimum height
- ✓ Options: 56dp+ minimum height
- ✓ Spacing: 4dp+ between interactive elements

### Text
- ✓ Clear, descriptive labels
- ✓ Timeout values shown (800ms, 1000ms, 1500ms)
- ✓ Use case descriptions
- ✓ Font size: 12sp (button), 16-18sp (dialog)

## State Persistence

### Profile Saved
```
User selects "Loud Truck"
    ↓
Profile saved to SharedPreferences
    ↓
Button shows "Loud Truck"
    ↓
App closes
    ↓
App reopens
    ↓
Button still shows "Loud Truck" ✓
```

### Profile Applied
```
Session starts
    ↓
Load saved profile from SharedPreferences
    ↓
Apply to STT manager
    ↓
Next listening uses new timeouts ✓
```

## Animation

### Dialog Opening
- Fade in: 200ms
- Scale: 95% → 100%
- Easing: FastOutSlowInEasing

### Profile Selection
- Highlight animation: 150ms
- Border color change: Smooth transition
- Icon appearance: Fade in

### Button Update
- Text change: Immediate
- Color change: Smooth transition (if applicable)

## Comparison with Answer Mode

### Answer Mode Button
```
[Settings] Long
```
- Green color (#00E676)
- Settings icon
- Shows "Short" or "Long"

### Noise Profile Button
```
[Mic] Loud Truck
```
- Yellow/amber color (#FFC107)
- Microphone icon
- Shows profile name

**Distinction:**
- Different colors (green vs. yellow)
- Different icons (settings vs. microphone)
- Different purposes (response length vs. audio timeout)
- Both in toolbar for easy access

## Summary

✓ **Toolbar Enhancement**
- Added noise profile button
- Compact design
- Clear visual distinction from Answer Mode

✓ **Dialog Design**
- 3 profile options
- Clear descriptions with timeout values
- Visual feedback for selection
- Easy to understand

✓ **User Experience**
- One-tap access to profile selection
- Immediate feedback
- Persistent across app restarts
- No disruption to listening

✓ **Accessibility**
- High contrast colors
- Clear labels
- Adequate touch targets
- Descriptive text
