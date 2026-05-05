package trucker.geminiflash.tools

import trucker.geminiflash.network.FunctionDeclaration
import trucker.geminiflash.network.Schema
import kotlinx.serialization.json.*

object TruckingTools {
    private const val DEMO_DRIVER_ID = "284145"
    private const val DEMO_ACTIVE_LOAD_ID = "902771"

    val declaration = listOf(
            FunctionDeclaration(
                name = "getDriverDashboard",
                description = "Returns driver profile info, current location/corridor, safety score with recent events, MPG performance with peer comparison, and personal goals (home-time countdown, miles this month, referral bonus). Does NOT include HOS details or medical card status - use getComplianceStatus for those.",
                parameters = Schema(
                    type = "OBJECT",
                    properties = emptyMap()
                )
            ),
            FunctionDeclaration(
                name = "getTruckInfo",
                description = "Returns truck and trailer equipment details including tractor/trailer numbers, trailer type, ELD provider, and equipment health metrics (DEF level, fuel percentage, tire tread, active fault codes, service milestones).",
                parameters = Schema(
                    type = "OBJECT",
                    properties = emptyMap()
                )
            ),
            FunctionDeclaration(
                name = "getLoadInformation",
                description = "Returns load details based on type. Use 'current' for active load with stops, ETAs, and facility insights (parking/amenities), or 'next' for pre-dispatch information.",
                parameters = Schema(
                    type = "OBJECT",
                    properties = mapOf(
                        "loadType" to Schema(type = "STRING", description = "Type of load: 'current' or 'next'")
                    ),
                    required = listOf("loadType")
                )
            ),
            FunctionDeclaration(
                name = "getFinancials",
                description = "Returns financial information based on period. Use 'current' for recent paycheck, 'ytd' for year-to-date totals, or 'bonus' for safety bonus program details.",
                parameters = Schema(
                    type = "OBJECT",
                    properties = mapOf(
                        "period" to Schema(type = "STRING", description = "Period: 'current', 'ytd', or 'bonus'")
                    ),
                    required = listOf("period")
                )
            ),
            FunctionDeclaration(
                name = "getRouteConditions",
                description = "Returns real-time weather and traffic conditions for the immediate route (next 1 hour) and recommended fuel stops with amenities. For load-specific route risks tied to a specific delivery, use getLoadInformation.",
                parameters = Schema(
                    type = "OBJECT",
                    properties = emptyMap()
                )
            ),
            FunctionDeclaration(
                name = "getCommunications",
                description = "Returns communication information. Use 'messages' for dispatch inbox or 'contacts' for support department phone numbers.",
                parameters = Schema(
                    type = "OBJECT",
                    properties = mapOf(
                        "type" to Schema(type = "STRING", description = "Type: 'messages' or 'contacts'"),
                        "unreadOnly" to Schema(type = "BOOLEAN", description = "For messages only: if true, return only unread items")
                    ),
                    required = listOf("type")
                )
            ),
            FunctionDeclaration(
                name = "getCompanyResources",
                description = "Returns company information based on category. Use 'policies' for FAQs and terminal amenities/parking status, 'mentor' for mentor program, 'ownerOperator' for lease program, or 'training' for modules.",
                parameters = Schema(
                    type = "OBJECT",
                    properties = mapOf(
                        "category" to Schema(type = "STRING", description = "Category: 'policies', 'mentor', 'ownerOperator', or 'training'")
                    ),
                    required = listOf("category")
                )
            ),
            FunctionDeclaration(
                name = "getComplianceStatus",
                description = "Returns HOS status (drive/duty/cycle remaining, 7-day recap, break clocks, alerts), medical card status, DVIR submission status, and annual inspection scheduling. This is the authoritative source for all regulatory compliance data.",
                parameters = Schema(
                    type = "OBJECT",
                    properties = emptyMap()
                )
            ),
            FunctionDeclaration(
                name = "closeApp",
                description = "Closes the application. Use when the driver explicitly requests to close, exit, or quit the app.",
                parameters = Schema(
                    type = "OBJECT",
                    properties = emptyMap()
                )
            )
        )

    /**
     * Convert declarations to Vertex AI Gen AI SDK Tool format.
     */
    fun getVertexTools(): List<com.google.genai.types.Tool> {
        val functionDeclarations = declaration.map { func ->
            val builder = com.google.genai.types.FunctionDeclaration.builder()
                .name(func.name)
            
            func.description?.let { builder.description(it) }
            func.parameters?.let { builder.parameters(it.toSdkSchema()) }
            
            builder.build()
        }
        return listOf(
            com.google.genai.types.Tool.builder()
                .functionDeclarations(functionDeclarations)
                .build()
        )
    }

    /**
     * Convert our Schema to Gen AI SDK Schema format.
     */
    private fun Schema.toSdkSchema(): com.google.genai.types.Schema {
        val builder = com.google.genai.types.Schema.builder()
        
        this.type?.let { builder.type(it) }
        this.description?.let { builder.description(it) }

        this.properties?.let { props ->
            val sdkProps = props.mapValues { (_, schema) ->
                schema.toSdkSchema()
            }
            builder.properties(sdkProps)
        }

        this.required?.let { builder.required(it) }
        this.items?.let { builder.items(it.toSdkSchema()) }

        return builder.build()
    }

    fun handleToolCall(name: String, args: Map<String, JsonElement>?): JsonElement {
        return when (name) {
            "getDriverDashboard" -> {
                buildJsonObject {
                    put("driver_id", DEMO_DRIVER_ID)
                    put("profile", buildJsonObject {
                        put("full_name", "Jordan Ramirez")
                        put("fleet", "Dry Van OTR")
                        put("home_terminal", "Phoenix, AZ")
                        put("cdl_class", "A")
                        put("tenure_years", 6)
                    })
                    put("location", buildJsonObject {
                        put("as_of", "2026-05-15T14:20")
                        put("nearest_city", "Flagstaff, AZ")
                        put("corridor", "I-40 EB")
                    })
                    put("personal_goals", buildJsonObject {
                        put("next_scheduled_hometime", "2026-05-22")
                        put("hometime_countdown_days", 7)
                        put("miles_this_month", 9420)
                        put("bonus_milestone_progress", "85%")
                        put("referral_bonus_pending", "$500 (1 driver in orientation)")
                    })
                    put("safety_score", buildJsonObject {
                        put("current_score", 945)
                        put("status", "Green / Bonus Eligible")
                        put("company_percentile", "Top 15%")
                        put("recent_events", buildJsonArray {
                            add(buildJsonObject {
                                put("event_type", "Hard Braking")
                                put("date", "2026-05-14")
                                put("location", "I-40 near Kingman, AZ")
                                put("severity", "Moderate")
                                put("impact_on_score", "-3 pts")
                            })
                        })
                    })
                    put("mpg_performance", buildJsonObject {
                        put("period", "4-week rolling average")
                        put("current_metrics", buildJsonObject {
                            put("mpg", 7.2)
                            put("idle_time_percentage", 12.5)
                            put("cruise_control_usage", 68.0)
                        })
                        put("peer_comparison", buildJsonObject {
                            put("fleet_average_mpg", 6.8)
                            put("fleet_percentile_mpg", "75th")
                            put("fuel_savings_vs_fleet", "$42.50 per week")
                        })
                    })
                }
            }
            
            "getTruckInfo" -> {
                buildJsonObject {
                    put("driver_id", DEMO_DRIVER_ID)
                    put("equipment", buildJsonObject {
                        put("tractor", "684821")
                        put("trailer", "903144")
                        put("trailer_type", "53ft Dry Van")
                        put("reefer_enabled", false)
                        put("eld_provider", "Samsara")
                    })
                    put("equipment_health", buildJsonObject {
                        put("def_level_percentage", 82)
                        put("fuel_level_percentage", 65)
                        put("next_service_milestone", "Oil change due in 2,450 miles")
                        put("tire_tread_status", "8/32 - Good")
                        put("active_fault_codes", buildJsonArray {
                            add("Sensor-ABS-Trailer (Non-critical)")
                        })
                    })
                }
            }
            
            "getLoadInformation" -> {
                val loadType = args?.get("loadType")?.jsonPrimitive?.contentOrNull ?: "current"
                if (loadType == "current") {
                    buildJsonObject {
                        put("driver_id", DEMO_DRIVER_ID)
                        put("load_id", DEMO_ACTIVE_LOAD_ID)
                        put("bol_number", "BOL-902771-4821")
                        put("status", "in_transit")
                        put("priority", "high")
                        put("load_type", "live_load")
                        put("customer", buildJsonObject {
                            put("name", "Walmart DC #213")
                            put("swift_csr_phone", "800-800-2200")
                            put("reference_number", "WMT-213-902771")
                        })
                        put("origin", "Reno, NV")
                        put("destination", "Dallas, TX")
                        put("next_stop_eta", "2026-05-15T19:40")
                        put("facility_insights", buildJsonObject {
                            put("overnight_parking_allowed", true)
                            put("bathroom_access", "Driver Lounge / 24-7")
                            put("average_detention_time", "2.5 hours")
                            put("entry_instructions", "Enter through North Gate on Miller Rd. Have CDL ready for security.")
                            put("on_site_scale", true)
                        })
                        put("stops", buildJsonArray {
                            add(buildJsonObject {
                                put("stop_index", 1)
                                put("type", "pickup")
                                put("facility", "Silver State Distribution")
                                put("city", "Reno, NV")
                                put("appointment", "2026-05-14T09:00")
                                put("arrival_time", "2026-05-14T08:45")
                                put("status", "completed")
                            })
                            add(buildJsonObject {
                                put("stop_index", 2)
                                put("type", "fuel")
                                put("facility", "Swift Fuel Network #AZ-17")
                                put("city", "Flagstaff, AZ")
                                put("appointment", "2026-05-15T19:30")
                                put("arrival", "2026-05-15T19:40")
                                put("status", "in_progress")
                                put("risk", "minor_delay_10m")
                            })
                            add(buildJsonObject {
                                put("stop_index", 3)
                                put("type", "delivery")
                                put("facility", "DFW Retail Crossdock")
                                put("city", "Dallas, TX")
                                put("appointment", "2026-05-16T13:00")
                                put("status", "pending")
                                put("risk", "tight_eta_due_to_i40_winds")
                            })
                        })
                        put("route_risks", buildJsonArray {
                            add(buildJsonObject {
                                put("segment", "I-40 EB mm 167-210")
                                put("risk_type", "crosswind")
                                put("severity", "medium")
                                put("confidence", 0.86)
                            })
                        })
                    }
                } else {
                    buildJsonObject {
                        put("driver_id", DEMO_DRIVER_ID)
                        put("load_id", "902812")
                        put("bol_number", "BOL-902812-9234")
                        put("status", "pending_dispatch")
                        put("load_type", "drop_hook")
                        put("preload_available", true)
                        put("customer", buildJsonObject {
                            put("name", "Atlanta Distribution Center (Target)")
                            put("swift_csr_phone", "800-800-2200")
                            put("reference_number", "TGT-ATL-902812")
                        })
                        put("origin", "Dallas, TX")
                        put("destination", "Atlanta, GA")
                        put("pickup_window", "2026-05-16T15:00 to 2026-05-16T19:00")
                        put("delivery_window", "2026-05-18T08:00 to 2026-05-18T12:00")
                        put("total_miles", 780)
                        put("equipment_required", "53ft Dry Van")
                        put("notes", "High value load, no unauthorized stops. Preload available at Dallas yard.")
                    }
                }
            }
            
            "getFinancials" -> {
                val period = args?.get("period")?.jsonPrimitive?.contentOrNull ?: "current"
                when (period) {
                    "current" -> buildJsonObject {
                        put("driver_id", DEMO_DRIVER_ID)
                        put("pay_period", "2026-04-28 to 2026-05-10")
                        put("pay_date", "2026-05-11")
                        put("base_pay", buildJsonObject {
                            put("miles_paid", 2850)
                            put("cpm_rate", 0.52)
                            put("base_amount", 1482.00)
                        })
                        put("accessorial_pay", buildJsonArray {
                            add(buildJsonObject {
                                put("type", "Layover")
                                put("amount", 150.00)
                                put("reason", "Weather delay in Flagstaff")
                                put("date", "2026-05-05")
                            })
                            add(buildJsonObject {
                                put("type", "Detention")
                                put("amount", 75.00)
                                put("reason", "Shipper delay - Reno")
                                put("date", "2026-05-02")
                            })
                        })
                        put("deductions", buildJsonArray {
                            add(buildJsonObject {
                                put("type", "Insurance")
                                put("amount", -85.50)
                                put("description", "Health/Dental/Vision")
                            })
                        })
                        put("net_amount", 1450.25)
                    }
                    "ytd" -> buildJsonObject {
                        put("driver_id", DEMO_DRIVER_ID)
                        put("ytd_gross", 16850.00)
                        put("ytd_net", 14250.75)
                        put("total_miles_ytd", 32400)
                        put("average_cpm_ytd", 0.52)
                    }
                    "bonus" -> buildJsonObject {
                        put("driver_id", DEMO_DRIVER_ID)
                        put("program_name", "Knight-Swift Safety Bonus Program")
                        put("current_quarter", "Q2 2026")
                        put("monthly_safety_class", buildJsonObject {
                            put("required", true)
                            put("month", "May 2026")
                            put("title", "Defensive Driving Techniques")
                            put("deadline", "2026-05-30")
                            put("status", "completed")
                            put("completion_date", "2026-05-15")
                            put("bonus_amount", 150.00)
                        })
                        put("quarterly_bonus_status", buildJsonObject {
                            put("eligible", true)
                            put("required_score", 900)
                            put("projected_bonus", 450.00)
                            put("payment_date", "2026-07-15")
                            put("note", "Check getDriverDashboard for current safety score")
                        })
                        put("total_projected_earnings", 600.00)
                    }
                    else -> buildJsonObject {
                        put("error", "Invalid period. Use 'current', 'ytd', or 'bonus'.")
                    }
                }
            }
            
            "getRouteConditions" -> {
                buildJsonObject {
                    put("driver_id", DEMO_DRIVER_ID)
                    put("time_horizon", "1 hour")
                    put("generated_at", "2026-05-15T14:20")
                    put("conditions", buildJsonArray {
                        add(buildJsonObject {
                            put("type", "weather")
                            put("impact", "High winds")
                            put("segment", "I-40 EB near Holbrook")
                            put("severity", "medium")
                            put("recommended_action", "Reduce speed and maintain firm grip on steering wheel.")
                        })
                        add(buildJsonObject {
                            put("type", "traffic")
                            put("impact", "Slow moving traffic")
                            put("segment", "I-40 EB mm 185-190")
                            put("severity", "low")
                            put("recommended_action", "Expect 5-10 minute delay.")
                        })
                    })
                    put("fuel_recommendation", buildJsonObject {
                        put("brand", "Pilot")
                        put("location", "Flagstaff, AZ (Exit 195)")
                        put("distance_miles", 12)
                        put("fuel_discount", "High")
                        put("amenities", buildJsonArray {
                            add("DEF at Pump")
                            add("Cat Scale")
                            add("Showers/Clean Bathrooms")
                            add("Restaurant")
                        })
                    })
                    put("restriction_warning", "Do NOT fuel at independent stops on this corridor; use only Pilot/Flying J/Love's or Swift yards.")
                }
            }
            
            "getCommunications" -> {
                val type = args?.get("type")?.jsonPrimitive?.contentOrNull ?: "messages"
                val unreadOnly = args?.get("unreadOnly")?.jsonPrimitive?.booleanOrNull ?: false
                if (type == "messages") {
                    val messages = buildJsonArray {
                        add(buildJsonObject {
                            put("message_id", "DSP-77101")
                            put("unread", true)
                            put("priority", "high")
                            put("subject", "Delivery gate code updated")
                            put("body", "DFW Retail Crossdock gate code is now 4729#. Confirm receipt.")
                            put("created_at", "2026-05-15T13:55")
                        })
                        add(buildJsonObject {
                            put("message_id", "DSP-77088")
                            put("unread", false)
                            put("priority", "normal")
                            put("subject", "Fuel stop preference")
                            put("body", "Use Swift Fuel Network #AZ-17 when practical.")
                            put("created_at", "2026-05-15T09:10")
                        })
                    }
                    val filteredMessages = if (unreadOnly) {
                        JsonArray(messages.filter { it.jsonObject["unread"]?.jsonPrimitive?.boolean == true })
                    } else {
                        messages
                    }
                    buildJsonObject { 
                        put("type", "messages")
                        put("messages", filteredMessages) 
                    }
                } else {
                    buildJsonObject {
                        put("type", "contacts")
                        put("driver_leader", buildJsonObject {
                            put("name", "Sarah Jenkins")
                            put("contact_method", "In-cab Macro or Driver Portal")
                            put("phone", "(602) 269-9700 ext 4561 (Phoenix Terminal)")
                            put("availability", "Mon-Fri 0800-1700")
                        })
                        put("fleet_leader", buildJsonObject {
                            put("name", "Marcus Reynolds (Dry Van OTR)")
                            put("contact_method", "In-cab Macro or Driver Portal")
                        })
                        put("departments", buildJsonArray {
                            add(buildJsonObject {
                                put("name", "Driver Support Services (24/7)")
                                put("phone", "800-555-0199")
                                put("function", "Urgent on-road needs, dispatch issues, routing")
                            })
                            add(buildJsonObject {
                                put("name", "On-Road Breakdown Support")
                                put("phone", "800-555-0188")
                                put("function", "Mechanical issues, repairs, authorization")
                            })
                            add(buildJsonObject {
                                put("name", "Payroll")
                                put("phone", "800-555-0177")
                            })
                        })
                    }
                }
            }
            
            "getCompanyResources" -> {
                val category = args?.get("category")?.jsonPrimitive?.contentOrNull ?: "policies"
                when (category) {
                    "policies" -> buildJsonObject {
                        put("company", "Swift Transportation")
                        put("last_updated", "2026-05-10")
                        put("categories", buildJsonArray {
                            add(buildJsonObject {
                                put("category", "Pet Policy")
                                put("policy_summary", "Swift allows company drivers to bring one dog, weighing 40 pounds or less.")
                            })
                            add(buildJsonObject {
                                put("category", "Rider Policy")
                                put("policy_summary", "Authorized riders are permitted with a valid permit.")
                            })
                            add(buildJsonObject {
                                put("category", "Breakdown SOP")
                                put("policy_summary", "Protocol for mechanical issues on the road.")
                            })
                        })
                        put("terminal_info", buildJsonObject {
                            put("current_terminal", "Phoenix Main")
                            put("parking_capacity", "Limited (75% full)")
                            put("amenities", buildJsonArray {
                                add("Showers (6)")
                                add("Laundry (Free)")
                                add("Driver Lounge (WiFi/TV)")
                                add("Cafeteria (0600-2200)")
                            })
                            put("shop_status", "Open 24/7")
                        })
                    }

                    "mentor" -> buildJsonObject {
                        put("program_name", "Swift Driver Mentor Program")
                        put("overview", "Pass along your knowledge to the next generation of drivers while enhancing your own earning potential.")
                        put("benefits", buildJsonArray {
                            add("Boost earning potential: Top 25% of mentors make $100,000 annually.")
                            add("Build connections and take control of your career path.")
                        })
                        put("requirements", buildJsonArray {
                            add("Class A CDL.")
                            add("Solid, safe driving record.")
                            add("Approval from Driver Leader, Terminal Leader, and Safety Leader.")
                        })
                    }
                    "ownerOperator" -> buildJsonObject {
                        put("program_name", "Swift Owner-Operator Program")
                        put("value_proposition", "Unlock your entrepreneurial spirit and take control of your destiny by becoming your own boss.")
                        put("financial_perks", buildJsonArray {
                            add("No credit checks: Accessible regardless of credit history.")
                            add("$0 down lease options: Start your journey without upfront costs.")
                            add("Percentage-based pay: 70% of the market rate for each load.")
                        })
                    }
                    "training" -> buildJsonObject {
                        put("driver_id", DEMO_DRIVER_ID)
                        put("required_modules", buildJsonArray {
                            add(buildJsonObject {
                                put("module_id", "SAFE-001")
                                put("title", "Defensive Driving Techniques")
                                put("type", "video")
                                put("duration_minutes", 25)
                                put("status", "completed")
                                put("completion_date", "2026-05-15")
                                put("link", "https://swiftuniversity.com/modules/safe-001")
                            })
                            add(buildJsonObject {
                                put("module_id", "HOS-002")
                                put("title", "Hours of Service Best Practices")
                                put("type", "interactive")
                                put("duration_minutes", 15)
                                put("status", "in_progress")
                                put("progress_percent", 60)
                                put("link", "https://swiftuniversity.com/modules/hos-002")
                            })
                        })
                    }
                    else -> buildJsonObject {
                        put("error", "Invalid category. Use 'policies', 'mentor', 'ownerOperator', or 'training'.")
                    }
                }
            }
            
            "getComplianceStatus" -> {
                buildJsonObject {
                    put("driver_id", DEMO_DRIVER_ID)
                    put("hos_compliance", buildJsonObject {
                        put("drive_hours_remaining", "5h 15m")
                        put("duty_time_remaining", "8h 45m")
                        put("cycle_hours_remaining", "18h 45m")
                        put("next_break_due_in", "2h 30m")
                        put("next_30m_break_due_by", "2026-05-15T17:05")
                        put("hos_recap", buildJsonObject {
                            put("hours_returning_at_midnight", "8.5")
                            put("eight_day_total", "61.5")
                            put("next_7_day_projection", buildJsonArray {
                                add(buildJsonObject { put("date", "2026-05-16"); put("hours_back", "8.5") })
                                add(buildJsonObject { put("date", "2026-05-17"); put("hours_back", "11.0") })
                                add(buildJsonObject { put("date", "2026-05-18"); put("hours_back", "0.0") })
                                add(buildJsonObject { put("date", "2026-05-19"); put("hours_back", "9.5") })
                                add(buildJsonObject { put("date", "2026-05-20"); put("hours_back", "10.0") })
                                add(buildJsonObject { put("date", "2026-05-21"); put("hours_back", "8.0") })
                                add(buildJsonObject { put("date", "2026-05-22"); put("hours_back", "7.5") })
                            })
                        })
                        put("alerts", buildJsonArray {
                            add(buildJsonObject {
                                put("category", "HOS")
                                put("severity", "warning")
                                put("message", "11-hour drive limit projected in 5h 15m.")
                                put("due_by", "2026-05-15T20:05")
                            })
                        })
                    })
                    put("medical_card_status", buildJsonObject {
                        put("expires_on", "2026-12-14")
                        put("days_until_expiry", 213)
                        put("reminder_scheduled", true)
                        put("next_reminder_date", "2026-09-15")
                        put("renewal_window_opens", "2026-10-14")
                        put("dot_physical_required", true)
                        put("preferred_clinics", buildJsonArray {
                            add("Concentra - Phoenix")
                            add("Urgent Care Plus - Flagstaff")
                            add("Swift Medical Partner - Tucson")
                        })
                    })
                    put("dvir_status", "submitted_today")
                    put("annual_inspection", buildJsonObject {
                        put("last_inspection_date", "2025-12-15")
                        put("next_inspection_due", "2026-12-15")
                        put("days_until_due", 214)
                    })
                }
            }

            "closeApp" -> {
                buildJsonObject {
                    put("action", "close_application")
                    put("message", "Closing the app. Drive safe.")
                }
            }

            else -> throw IllegalArgumentException("Unknown tool: $name")
        }
    }
}




