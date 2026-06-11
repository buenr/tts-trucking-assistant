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
- **Safety:** Current score, status, fleet percentile, recent hard braking/safety events with dates and locations.
- **MPG:** 4-week rolling average, idle %, cruise control usage, peer comparison (percentile, fuel savings vs fleet average).

**Demo Data Example:**
- Driver: Jordan Ramirez (6 years tenure, Dry Van OTR fleet)
- Location: Flagstaff, AZ on I-40 EB
- Safety Score: 945 (Top 15%, Bonus Eligible)
- Recent Event: Hard braking on 5/14 near Kingman, AZ (-3 pts)
- MPG: 7.2 (fleet avg 6.8, saving $42.50/week)
- Home-time: 7 days remaining (5/22)
- Referral Bonus: $500 pending (1 driver in orientation)

**Note:** For HOS status, medical card status, and DVIR status, use `getComplianceStatus`.

**Example Questions:**
- "Where am I currently located?"
- "How's my safety score and what's my company percentile?"
- "What is my MPG performance and idle time?"
- "How many days until my next scheduled home-time?"
- "How many miles have I driven this month?"
- "What's my referral bonus status?"
- "Tell me about my recent safety events."
- "How much fuel am I saving compared to the fleet average?"

---

## 2. Truck Information (`getTruckInfo`)
Returns truck and trailer equipment details.

**Parameters:** None

**Data Points Returned:**
- **Equipment:** Tractor number, trailer number, trailer type, reefer capability, ELD provider.
- **Health:** DEF level (%), fuel level (%), tire tread status (with measurements), active fault codes with severity.
- **Maintenance:** Next service milestone with distance remaining.

**Demo Data Example:**
- Tractor: 684821
- Trailer: 903144 (53ft Dry Van, no reefer)
- ELD Provider: Samsara
- DEF Level: 82%
- Fuel Level: 65%
- Tire Tread: 8/32 - Good
- Fault Codes: Sensor-ABS-Trailer (Non-critical)
- Next Service: Oil change due in 2,450 miles

**Example Questions:**
- "What's my truck and trailer number?"
- "What's my trailer type?"
- "What's my ELD provider?"
- "Check my equipment health: what are my DEF and fuel levels?"
- "Do I have any active fault codes?"
- "When is my next service due?"
- "How's my tire tread?"
- "Is my trailer a reefer?"

---

## 3. Load Information (`getLoadInformation`)
Provides detailed tracking for active and upcoming loads.

**Parameters:**
- `loadType` (String, Required): Use `"current"` for active load or `"next"` for pre-dispatch.

**Data Points Returned:**
- **Core:** Load ID, BOL number, status (in_transit/pending_dispatch), priority, load type (live_load vs. drop_hook).
- **Customer:** Name, reference number, Swift CSR phone.
- **Trip:** Origin, destination, total miles, pickup/delivery windows and appointments.
- **Stops:** Full list of stops with types (pickup/fuel/delivery), cities, appointments, ETAs, arrival times, and status.
- **Insights:** Overnight parking availability, bathroom/lounge access, average detention time, entry instructions, on-site scales.
- **Risks:** Route risks (e.g., crosswinds, traffic) with severity and confidence scores.

**Demo Data - Current Load:**
- Load ID: 902771, BOL: BOL-902771-4821
- Status: In Transit (High Priority)
- Customer: Walmart DC #213 (Reference: WMT-213-902771)
- Route: Reno, NV → Dallas, TX
- Stops:
  1. Silver State Distribution (Reno) - Pickup - Completed 5/14 08:45
  2. Swift Fuel Network #AZ-17 (Flagstaff) - Fuel - In Progress, ETA 5/15 19:40
  3. DFW Retail Crossdock (Dallas) - Delivery - Pending, ETA 5/16 13:00
- Facility Insights: Overnight parking allowed, 24/7 driver lounge, 2.5hr avg detention, on-site scale
- Entry: "Enter through North Gate on Miller Rd. Have CDL ready for security."
- Route Risk: I-40 EB crosswinds (medium severity, 86% confidence)

**Demo Data - Next Load:**
- Load ID: 902812, BOL: BOL-902812-9234
- Status: Pending Dispatch (Drop & Hook)
- Customer: Atlanta Distribution Center (Target)
- Route: Dallas, TX → Atlanta, GA (780 miles)
- Pickup Window: 5/16 15:00-19:00
- Delivery Window: 5/18 08:00-12:00
- Preload Available: Yes (at Dallas yard)
- Notes: High value load, no unauthorized stops

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
- "What's the delivery appointment window for my next load?"
- "What's the priority level of my current load?"

---

## 4. Financial Information (`getFinancials`)
Retrieves detailed pay and bonus program data.

**Parameters:**
- `period` (String, Required): Use `"current"` for the last paycheck, `"ytd"` for yearly totals, or `"bonus"` for safety bonus details.

**Data Points Returned:**
- **Current Pay:** Net amount, pay date, pay period, base pay (miles/CPM rate), accessorials (layover/detention with reasons and dates), and deductions (insurance with description).
- **YTD:** Gross/net totals, total miles YTD, average CPM.
- **Bonus:** Program name, quarterly eligibility, projected bonus amount, payment dates, and required safety class status (titles, deadlines, completion dates, bonus amounts).

**Demo Data - Current Pay (5/11/2026):**
- Pay Period: 4/28-5/10
- Base Pay: 2,850 miles @ $0.52 CPM = $1,482.00
- Accessorials:
  - Layover: $150 (Weather delay in Flagstaff, 5/5)
  - Detention: $75 (Shipper delay in Reno, 5/2)
- Deductions: Insurance -$85.50 (Health/Dental/Vision)
- Net Amount: $1,450.25

**Demo Data - YTD:**
- Gross: $16,850.00
- Net: $14,250.75
- Total Miles: 32,400
- Average CPM: $0.52

**Demo Data - Bonus (Q2 2026):**
- Program: Knight-Swift Safety Bonus Program
- Monthly Class: "Defensive Driving Techniques" (Completed 5/15, $150 bonus)
- Quarterly Bonus: Eligible, requires 900 safety score (current: 945), projected $450 (payment 7/15)
- Total Projected: $600

**Example Questions:**
- "How much was my last paycheck?"
- "What was my net pay and the pay date for my last check?"
- "What was the insurance deduction on my last pay stub?"
- "How much layover or detention pay did I get this week?"
- "What is my year-to-date gross and net income?"
- "Am I eligible for my quarterly safety bonus?"
- "How much is my projected safety bonus?"
- "What safety classes do I need to complete for my bonus?"
- "When will my quarterly bonus be paid?"
- "What's my average CPM year-to-date?"

---

## 5. Route Conditions (`getRouteConditions`)
Provides real-time weather and traffic conditions for the immediate route (next 1 hour) and fueling recommendations. For load-specific route risks tied to a specific delivery, use `getLoadInformation`.

**Parameters:** None

**Data Points Returned:**
- **Conditions:** Weather and traffic impacts by segment with type, impact description, severity (low/medium/high), and recommended actions.
- **Fuel:** Recommended brand, specific location/distance, discount level (High/Medium/Low), and amenities (DEF at pump, scales, showers, restaurants).
- **Restrictions:** Corridor-specific fueling warnings (e.g., "Use only Pilot/Flying J/Love's or Swift yards").

**Demo Data (Generated 5/15 14:20):**
- Weather: High winds on I-40 EB near Holbrook (Medium severity) - "Reduce speed and maintain firm grip on steering wheel."
- Traffic: Slow moving traffic I-40 EB mm 185-190 (Low severity) - "Expect 5-10 minute delay."
- Fuel Recommendation:
  - Brand: Pilot
  - Location: Flagstaff, AZ (Exit 195)
  - Distance: 12 miles
  - Discount: High
  - Amenities: DEF at Pump, Cat Scale, Showers/Clean Bathrooms, Restaurant
- Restriction: "Do NOT fuel at independent stops on this corridor; use only Pilot/Flying J/Love's or Swift yards."

**Example Questions:**
- "Any traffic or weather delays in the next hour?"
- "Where is the best place to fuel up nearby?"
- "What amenities are at the recommended Pilot stop?"
- "Does the next fuel stop have DEF at the pump?"
- "Are there any fuel restrictions on this corridor?"
- "What's the severity of the weather ahead?"
- "How far is the next recommended fuel stop?"

---

## 6. Communications (`getCommunications`)
Accesses dispatch messages and company contacts.

**Parameters:**
- `type` (String, Required): Use `"messages"` for inbox or `"contacts"` for phone numbers.
- `unreadOnly` (Boolean, Optional): If true, returns only unread messages.

**Data Points Returned:**
- **Messages:** Message ID, priority (high/normal), subject, body, and timestamp.
- **Contacts:** Names and phone numbers for Driver Leader, Fleet Leader, Payroll, Breakdown (24/7), and Support Services with availability/function.

**Demo Data - Messages:**
- Message 1 (Unread, High Priority): "Delivery gate code updated" - "DFW Retail Crossdock gate code is now 4729#. Confirm receipt." (5/15 13:55)
- Message 2 (Read, Normal): "Fuel stop preference" - "Use Swift Fuel Network #AZ-17 when practical." (5/15 09:10)

**Demo Data - Contacts:**
- Driver Leader: Sarah Jenkins, (602) 269-9700 ext 4561 (Phoenix Terminal), Mon-Fri 0800-1700
- Fleet Leader: Marcus Reynolds (Dry Van OTR)
- Driver Support Services (24/7): 800-555-0199 (Urgent on-road needs, dispatch issues, routing)
- On-Road Breakdown Support: 800-555-0188 (Mechanical issues, repairs, authorization)
- Payroll: 800-555-0177

**Example Questions:**
- "Do I have any new messages from dispatch?"
- "Read my unread messages."
- "What is the gate code for my delivery?" (from message body)
- "What's the phone number for the Payroll department?"
- "How do I reach the On-Road Breakdown team?"
- "Who is my Driver Leader and what's their number?"
- "What's the phone number for Driver Support Services?"
- "When is my Driver Leader available?"

---

## 7. Company Resources (`getCompanyResources`)
Provides access to company manuals, programs, and terminal info.

**Parameters:**
- `category` (String, Required): Use `"policies"`, `"mentor"`, `"ownerOperator"`, or `"training"`.

**Data Points Returned:**
- **Policies:** Pet policy, rider policy, breakdown SOP, terminal info (parking capacity, amenities, shop status).
- **Terminal:** Parking capacity (e.g., "75% full"), shop hours, and amenities (showers, laundry, lounge, cafeteria).
- **Programs:** Mentor program benefits/requirements, Owner-Operator lease terms ($0 down, 70% pay).
- **Training:** Required modules with titles, types (video/interactive), duration, progress %, and deadlines.

**Demo Data - Policies:**
- Pet Policy: "Swift allows company drivers to bring one dog, weighing 40 pounds or less."
- Rider Policy: "Authorized riders are permitted with a valid permit."
- Breakdown SOP: "Protocol for mechanical issues on the road."
- Terminal (Phoenix Main): 75% parking full, Showers (6), Laundry (Free), Driver Lounge (WiFi/TV), Cafeteria (0600-2200), Shop (24/7)

**Demo Data - Mentor Program:**
- Overview: "Pass along your knowledge to the next generation of drivers while enhancing your own earning potential."
- Benefits: "Boost earning potential: Top 25% of mentors make $100,000 annually. Build connections and take control of your career path."
- Requirements: Class A CDL, solid safe driving record, approval from Driver Leader/Terminal Leader/Safety Leader

**Demo Data - Owner-Operator Program:**
- Value: "Unlock your entrepreneurial spirit and take control of your destiny by becoming your own boss."
- Financial Perks: No credit checks, $0 down lease options, 70% of market rate per load

**Demo Data - Training:**
- Module 1: "Defensive Driving Techniques" (Video, 25 min) - Completed 5/15
- Module 2: "Hours of Service Best Practices" (Interactive, 15 min) - In Progress (60%)

**Example Questions:**
- "What is the pet or rider policy?"
- "What's the protocol for a breakdown?"
- "How much parking is available at the Phoenix terminal?"
- "Is the shop at the terminal open 24/7?"
- "Tell me about the Mentor program requirements."
- "What are the benefits of becoming an Owner-Operator?"
- "Do I have any training modules to watch?"
- "What's the status of my training modules?"
- "How long is the Defensive Driving training?"

---

## 8. Compliance Status (`getComplianceStatus`)
Detailed view of regulatory and safety compliance. This is the authoritative source for all HOS and compliance data.

**Parameters:** None

**Data Points Returned:**
- **HOS:** Drive/duty/cycle remaining (formatted as hours:minutes), next break due with specific time, 30-min break clock deadline, 7-day recap (hours returning at midnight with projections), and specific alerts/warnings with severity and deadlines.
- **Medical:** Expiry date, days remaining, reminder schedule, renewal window open date, DOT physical requirements, and preferred clinics.
- **Inspection:** Last inspection date, next inspection due date, and days remaining.
- **DVIR:** Submission status for the current day.

**Demo Data (Current Time: 5/15 14:20):**
- Drive Hours Remaining: 5h 15m
- Duty Time Remaining: 8h 45m
- Cycle Hours Remaining: 18h 45m
- Next Break Due In: 2h 30m
- Next 30-Min Break Due By: 5/15 17:05
- HOS Recap:
  - Hours Returning at Midnight: 8.5
  - 8-Day Total: 61.5
  - 7-Day Projection: 5/16 (8.5h), 5/17 (11.0h), 5/18 (0h), 5/19 (9.5h), 5/20 (10.0h), 5/21 (8.0h), 5/22 (7.5h)
- Alert: "11-hour drive limit projected in 5h 15m." (Warning, due 5/15 20:05)
- Medical Card: Expires 12/14/2026 (213 days), renewal window opens 10/14/2026, DOT physical required
- Preferred Clinics: Concentra - Phoenix, Urgent Care Plus - Flagstaff, Swift Medical Partner - Tucson
- DVIR Status: Submitted today
- Annual Inspection: Last 12/15/2025, next due 12/15/2026 (214 days)

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
- "When is my next break due?"
- "How much duty time do I have remaining?"

---

## 9. Close Application (`closeApp`)
Closes the assistant.

**Example Triggers:**
- "Close the app."
- "I'm done, quit."
- "Goodbye."