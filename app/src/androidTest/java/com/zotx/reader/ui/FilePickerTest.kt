package com.zotx.reader.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zotx.reader.ui.BibPDFApp
import com.zotx.reader.ui.components.FilePicker
import com.zotx.reader.ui.viewmodel.PaperListViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FilePickerTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun filePicker_displaysCorrectInitialState() {
        composeTestRule.setContent {
            FilePicker()
        }

        // BibTeX file picker
        composeTestRule.onNodeWithText("Select BibTeX File")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Select")
            .assertIsDisplayed()

        // PDF folder picker
        composeTestRule.onNodeWithText("Select PDF Folder")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Select")
            .assertIsDisplayed()
    }

    @Test
    fun filePicker_updateButtonDisabledInitially() {
        composeTestRule.setContent {
            FilePicker()
        }

        composeTestRule.onNodeWithText("Update Papers")
            .assertIsDisplayed()
            .assertIsNotEnabled()
    }

    @Test
    fun filePicker_updateButtonEnabledAfterSelection() {
        composeTestRule.setContent {
            FilePicker()
        }

        // Simulate file selection
        composeTestRule.onNodeWithText("Select BibTeX File")
            .performClick()
        composeTestRule.onNodeWithText("Select PDF Folder")
            .performClick()

        composeTestRule.onNodeWithText("Update Papers")
            .assertIsDisplayed()
            .assertIsEnabled()
    }
}
