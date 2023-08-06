import { Capacitor } from '@capacitor/core';
import { Filesystem } from '@capacitor/filesystem';
import { TextToSpeech } from '@capacitor-community/text-to-speech';
import { Clipboard } from '@capacitor/clipboard';
import { Share } from '@capacitor/share';

console.info(`GH Maps`, 'Loaded App.js');

if (Capacitor.isNativePlatform()) {
    console.info(`GH Maps TTS`, `Register Text To Speech in the Window`);

    window.speechSynthesis = {
        speak(utterance) {
            console.error(`GH Maps TTS`, `Text to Speach was requested for "${utterance.text}" using ${utterance.lang}`)
            TextToSpeech.isLanguageSupported({lang: this.locale})
                .then((supported) => {
                    if (supported) {
                        this._speak(utterance.text, utterance.lang)
                    } else {
                        console.error(`GH Maps TTS`, `${this.locale} is not supported, we will fallback to the first available locale`);
                        TextToSpeech.getSupportedLanguages()
                            .then((languages) => {
                                console.error(`GH Maps TTS`, 'Suported languages are', ...languages);
                                this._speak(utterance.text, languages[0]);
                            })
                    }
                })
        },
        _speak(text, lang) {
            TextToSpeech.speak({
                text: text,
                lang: lang,
            })
                .catch((e) => console.error(`GH Maps TTS`, `Cannot speak  "${text}" using ${lang}`, e));
        }
    }

    window.SpeechSynthesisUtterance = function (_text) {
        return {
            text: _text,
            // Assume en-US as default, will be overwritten later on
            lang: 'en-US',
        }
    }

    window.navigator.clipboard.writeText = (text) => {
        return Clipboard.write({ string: text })
    }

    window.navigator.share = Share.share

    // Overwrite the experimental showSaveFilePicker function to make GPX download working for CapacitorJS.
    // The 'fileContents' parameter is not part of the normal API but we pass it along in RoutingResults.tsx to make it working.
    window.showSaveFilePicker = ({ suggestedName, types, fileContents }) => {
        try {
          Filesystem.writeFile({
            path: suggestedName,
            data: fileContents,
            directory: 'CACHE',
            encoding: 'utf8',
          })
          .then((writeFileResult) => {
              if (writeFileResult)
                  Share.share({ url: writeFileResult.uri })
              else
                  console.error('writeFileResult is not defined')
          })

          console.info('File written successfully.');
        } catch (error) {
          console.error('Error writing file:', error);
        }
    }
}

// We use require to make sure that gh is loaded after we init our script, import does not guarantee order
const gh = require('../graphhopper-maps/dist/bundle');

export default gh;