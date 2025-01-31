/*
 * Copyright (c) 2023 New Vector Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.element.android.features.messages

import io.element.android.features.messages.impl.MessagesNavigator
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.timeline.item.TimelineItemDebugInfo

class FakeMessagesNavigator : MessagesNavigator {
    var onShowEventDebugInfoClickedCount = 0
        private set

    var onForwardEventClickedCount = 0
        private set

    var onReportContentClickedCount = 0
        private set

    override fun onShowEventDebugInfoClicked(eventId: EventId?, debugInfo: TimelineItemDebugInfo) {
        onShowEventDebugInfoClickedCount++
    }

    override fun onForwardEventClicked(eventId: EventId) {
        onForwardEventClickedCount++
    }

    override fun onReportContentClicked(eventId: EventId, senderId: UserId) {
        onReportContentClickedCount++
    }
}
