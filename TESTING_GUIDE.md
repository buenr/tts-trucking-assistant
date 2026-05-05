# Trucking Assistant Testing Guide

This guide outlines the available tool calls in the Trucking Assistant, their parameters, and the data points they return. Use the example questions to trigger specific tools during testing.

---

## 1. Driver Dashboard (`getDriverDashboard`)
Returns driver profile, location, safety score, MPG performance, and personal goals.

**Parameters:** None

**Data Points Returned:**
- **Profile:** Full name, fleet, home terminal, CDL class, tenure.
- **Location:** Nearest city, corridor (e.g., I-40 EB), timestamp.
- **Goals:** Home-time countdown (days/date), miles this month, bonus progress, referral bonus status.
- **Safety:** Current score, status, fleet percentile, recent hard braking/safety events.
- **MPG:** 4-week average, idle %, cruise usage, peer comparison (percentile, fuel savings).

**Note:** For HOS status, medical card status, and DVIR status, use `getComplianceStatus`.

**Example Questions:**
- "Where am I currently located?"
- "How's my safety score and what's my company percentile?"
- "What is my MPG performance and idle time?"
- "How many days until my next scheduled home-time?"
- "How many miles have I driven this month?"
- "What's my referral bonus status?"

---

## 2. Truck Information (`getTruckInfo`)
Returns truck and trailer equipment details.

**Parameters:** None

**Data Points Returned:**
- **Equipment:** Tractor number, trailer number, trailer type, ELD provider.
- **Health:** DEF level, fuel level percentage, tire tread status, active fault codes.
- **Maintenance:** Next service milestone.

**Example Questions:**
- "What's my truck and trailer number?"
- "What's my trailer type?"
- "What's my ELD provider?"
- "Check my equipment health: what are my DEF and fuel levels?"
- "Do I have any active fault codes?"
- "When is my next service due?"
- "How's my tire tread?"

---

## 3. Load Information (`getLoadInformation`)
Provides detailed tracking for active and upcoming loads.

**Parameters:**
- `loadType` (String, Required): Use `"current"` for active load or `"next"` for pre-dispatch.

**Data Points Returned:**
- **Core:** Load ID, BOL number, status, priority, load type (live load vs. drop & hook).
- **Customer:** Name, reference number, Swift CSR phone.
- **Trip:** Origin, destination, total miles, pickup/delivery windows.
- **Stops:** Full list of stops with types (pickup/fuel/delivery), cities, appointments, ETAs, and status.
- **Insights:** Overnight parking availability, bathroom/lounge access, average detention time, entry instructions, on-site scales.
- **Risks:** Route risks (e.g., crosswinds, traffic) with severity and confidence scores.

**Example Questions:**
- "What is the BOL number for my current load?"
- "When is my next stop and what's the ETA?"
- "Who is the customer and what's their phone number?"
- "Are there any route risks like high winds ahead?"
- "Does the receiver have overnight parking or a scale?"
- "What are the entry instructions for this facility?"
- "What's the average detention time at my destination?"
- "Tell me about my next load: is it a drop and hook?"
- "How many total miles is my next trip?"
- "Is there a preload available for my next load?"

---

## 4. Financial Information (`getFinancials`)
Retrieves detailed pay and bonus program data.

**Parameters:**
- `period` (String, Required): Use `"current"` for the last paycheck, `"ytd"` for yearly totals, or `"bonus"` for safety bonus details.

**Data Points Returned:**
- **Current Pay:** Net amount, pay date, pay period, base pay (miles/rate), accessorials (layover/detention with reasons), and deductions (insurance).
- **YTD:** Gross/net totals, total miles YTD, average CPM.
- **Bonus:** Program name, quarterly eligibility, projected bonus amount, payment dates, and required safety class status (titles, deadlines, completion).

**Example Questions:**
- "How much was my last paycheck?"
- "What was my net pay and the pay date for my last check?"
- "What was the insurance deduction on my last pay stub?"
- "How much layover or detention pay did I get this week?"
- "What is my year-to-date gross and net income?"
- "Am I eligible for my quarterly safety bonus?"
- "How much is my projected safety bonus?"
- "What safety classes do I need to complete for my bonus?"

---

## 5. Route Conditions (`getRouteConditions`)
Provides real-time weather and traffic conditions for the immediate route (next 1 hour) and fueling recommendations. For load-specific route risks tied to a specific delivery, use `getLoadInformation`.

**Parameters:** None

**Data Points Returned:**
- **Conditions:** Weather and traffic impacts by segment (e.g., "High winds on I-40") with severity and recommended actions.
- **Fuel:** Recommended brand, specific location/distance, discount level, and amenities (DEF at pump, scales, showers, restaurants).
- **Restrictions:** Corridor-specific fueling warnings (e.g., "Use only Pilot/Flying J").

**Example Questions:**
- "Any traffic or weather delays in the next hour?"
- "Where is the best place to fuel up nearby?"
- "What amenities are at the recommended Pilot stop?"
- "Does the next fuel stop have DEF at the pump?"
- "Are there any fuel restrictions on this corridor?"

---

## 6. Communications (`getCommunications`)
Accesses dispatch messages and company contacts.

**Parameters:**
- `type` (String, Required): Use `"messages"` for inbox or `"contacts"` for phone numbers.
- `unreadOnly` (Boolean, Optional): If true, returns only unread messages.

**Data Points Returned:**
- **Messages:** Message ID, priority, subject, body, and timestamp.
- **Contacts:** Names and phone numbers for Driver Leader, Fleet Leader, Payroll, Breakdown (24/7), and Support Services.

**Example Questions:**
- "Do I have any new messages from dispatch?"
- "Read my unread messages."
- "What is the gate code for my delivery?" (from message body)
- "What's the phone number for the Payroll department?"
- "How do I reach the On-Road Breakdown team?"
- "Who is my Driver Leader and what's their number?"

---

## 7. Company Resources (`getCompanyResources`)
Provides access to company manuals, programs, and terminal info.

**Parameters:**
- `category` (String, Required): Use `"policies"`, `"mentor"`, `"ownerOperator"`, or `"training"`.

**Data Points Returned:**
- **Policies:** Pet policy, rider policy, breakdown SOP.
- **Terminal:** Parking capacity (e.g., "75% full"), shop hours, and amenities (showers, laundry, lounge, cafeteria).
- **Programs:** Mentor program benefits/requirements, Owner-Operator lease terms ($0 down, 70% pay).
- **Training:** Required modules, titles, types (video/interactive), duration, progress, and deadlines.

**Example Questions:**
- "What is the pet or rider policy?"
- "What's the protocol for a breakdown?"
- "How much parking is available at the Phoenix terminal?"
- "Is the shop at the terminal open 24/7?"
- "Tell me about the Mentor program requirements."
- "What are the benefits of becoming an Owner-Operator?"
- "Do I have any training modules to watch?"

---

## 8. Compliance Status (`getComplianceStatus`)
Detailed view of regulatory and safety compliance. This is the authoritative source for all HOS and compliance data.

**Parameters:** None

**Data Points Returned:**
- **HOS:** Drive/duty/cycle remaining, next break due (including 30-min break clock), 7-day recap (hours returning at midnight, projections), and specific alerts/warnings with deadlines.
- **Medical:** Expiry date, days remaining, renewal window, DOT physical requirements, and preferred clinics.
- **Inspection:** Annual tractor inspection due date and days remaining.
- **DVIR:** Submission status for the current day.

**Example Questions:**
- "Am I in compliance?"
- "How much drive time do I have left?"
- "Tell me about my HOS recap for the next week."
- "Do I have any HOS alerts or warnings?"
- "When does my medical card expire and when can I renew it?"
- "Do I need a DOT physical?"
- "Where can I get my DOT physical?"
- "When is my annual tractor inspection due?"
- "Is my DVIR submitted for today?"

---

## 9. Close Application (`closeApp`)
Closes the assistant.

**Example Triggers:**
- "Close the app."
- "I'm done, quit."
- "Goodbye."