# Trucking Assistant Testing Guide

This guide outlines the available tool calls in the Trucking Assistant and provides example questions you can ask to trigger them during testing.

## 1. Driver Dashboard (`getDriverDashboard`)
Returns comprehensive driver profile information, Hours of Service (HOS) status, safety score, MPG performance metrics, and medical card renewal reminders.

**Example Questions:**
* "What is my current safety score?"
* "How many drive hours do I have remaining?"
* "What is my MPG performance for the last 4 weeks?"
* "When does my medical card expire?"
* "What is my company percentile for MPG?"

## 2. Load Information (`getLoadInformation`)
Provides details about your loads. You can ask for your current active load (with stops and ETAs) or your next pre-dispatched load.

**Example Questions:**
* "What are the details of my current load?"
* "Where is my next stop and when am I scheduled to arrive?"
* "Are there any route risks on my current trip?"
* "What is my next load after this one?"
* "Do I have any pre-dispatch information?"

## 3. Financial Information (`getFinancials`)
Retrieves your financial data. You can ask for your current paycheck, year-to-date (YTD) totals, or details about the safety bonus program.

**Example Questions:**
* "How much is my net pay for the current pay period?"
* "What were my accessorial pays for this week?"
* "What is my year-to-date gross income?"
* "Am I eligible for the quarterly safety bonus?"
* "How much is my projected safety bonus?"

## 4. Route Conditions (`getRouteConditions`)
Gives you route planning information, including weather/traffic conditions for the next hour and recommended fuel stops with amenities.

**Example Questions:**
* "Are there any weather or traffic delays on my route for the next hour?"
* "Where is the best place to get fuel nearby?"
* "What amenities are available at the next recommended fuel stop?"
* "Are there any route conditions I should be aware of?"

## 5. Communications (`getCommunications`)
Provides access to your dispatch inbox messages or contact information for support departments.

**Example Questions:**
* "Do I have any unread messages from dispatch?"
* "Read my messages."
* "What is the phone number for the Payroll department?"
* "How do I contact my Driver Leader?"
* "Give me the contact info for On-Road Breakdown Support."

## 6. Company Resources (`getCompanyResources`)
Provides company information across various categories: policies, mentor program, owner-operator program, or training modules.

**Example Questions:**
* "What is the company's pet policy?"
* "Tell me about the Swift Driver Mentor Program."
* "What are the benefits of the Owner-Operator program?"
* "Do I have any required safety training modules to complete?"
* "What is the rider policy?"

## 7. Compliance Status (`getComplianceStatus`)
Returns compliance-focused information such as HOS alerts, medical card status, DVIR requirements, and annual inspection scheduling.

**Example Questions:**
* "What is my overall compliance status?"
* "Do I have any HOS alerts?"
* "When is my annual tractor inspection due?"
* "Is my DVIR submitted for today?"

## 8. Close Application (`closeApp`)
Closes the application when you are done.

**Example Triggers:**
* "Close the app."
* "Exit the application."
* "Goodbye."