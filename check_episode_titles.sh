#!/bin/bash

# Script to fetch and display episode titles for BBC National stations
# Uses the BBC ESS (Electronic Programme Schedule) API

echo "Fetching current episode titles for BBC National Stations..."
echo "================================================================"
echo ""

# National stations with their service IDs
declare -A stations=(
    ["BBC Radio 1"]="bbc_radio_one"
    ["BBC Radio 1Xtra"]="bbc_1xtra"
    ["BBC Radio 1 Dance"]="bbc_radio_one_dance"
    ["BBC Radio 2"]="bbc_radio_two"
    ["BBC Radio 3"]="bbc_radio_three"
    ["BBC Radio 4"]="bbc_radio_fourfm"
    ["BBC Radio 4 Extra"]="bbc_radio_four_extra"
    ["BBC Radio 5 Live"]="bbc_radio_five_live"
    ["BBC Radio 6 Music"]="bbc_6music"
    ["BBC World Service"]="bbc_world_service"
    ["BBC Asian Network"]="bbc_asian_network"
)

for station in "${!stations[@]}"; do
    service_id="${stations[$station]}"
    url="https://ess.api.bbci.co.uk/schedules?serviceId=${service_id}"
    
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "Station: $station"
    echo "Service ID: $service_id"
    echo ""
    
    # Fetch the schedule data
    response=$(curl -s "$url" -H "User-Agent: AndroidAutoRadioPlayer/1.0")
    
    if [ $? -eq 0 ]; then
        # Get current time in milliseconds
        current_time=$(date +%s)000
        
        # Parse JSON to find current show
        # This uses jq if available, otherwise uses grep/sed (less precise)
        if command -v jq &> /dev/null; then
            brand_title=$(echo "$response" | jq -r --arg now "$current_time" '.items[] | select(
                (.published_time.start | sub("\\.\\d+Z$"; "Z") | fromdateiso8601 * 1000) <= ($now | tonumber) and 
                (.published_time.end | sub("\\.\\d+Z$"; "Z") | fromdateiso8601 * 1000) > ($now | tonumber)
            ) | .brand.title // "N/A"' | head -1)
            
            episode_title=$(echo "$response" | jq -r --arg now "$current_time" '.items[] | select(
                (.published_time.start | sub("\\.\\d+Z$"; "Z") | fromdateiso8601 * 1000) <= ($now | tonumber) and 
                (.published_time.end | sub("\\.\\d+Z$"; "Z") | fromdateiso8601 * 1000) > ($now | tonumber)
            ) | .episode.title // "N/A"' | head -1)
            
            start_time=$(echo "$response" | jq -r --arg now "$current_time" '.items[] | select(
                (.published_time.start | sub("\\.\\d+Z$"; "Z") | fromdateiso8601 * 1000) <= ($now | tonumber) and 
                (.published_time.end | sub("\\.\\d+Z$"; "Z") | fromdateiso8601 * 1000) > ($now | tonumber)
            ) | .published_time.start // "N/A"' | head -1)
            
            if [ -n "$brand_title" ] && [ "$brand_title" != "" ]; then
                echo "Brand Title: $brand_title"
                echo "Episode Title: $episode_title"
                echo "Time: $start_time"
            else
                echo "No current show found in schedule"
            fi
        else
            # Fallback: just show the first few items without time filtering
            echo "$response" | grep -oP '"title":\s*"\K[^"]+' | head -4 | nl
            echo "(Install 'jq' for better parsing)"
        fi
    else
        echo "Error fetching data"
    fi
    
    echo ""
done

echo "================================================================"
echo "Complete!"
