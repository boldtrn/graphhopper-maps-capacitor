import {Capacitor} from '@capacitor/core';
import {TextToSpeech} from '@capacitor-community/text-to-speech';

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
}

// We use require to make sure that gh is loaded after we init our script, import does not guarantee order
const gh = require('../graphhopper-maps/dist/bundle');

export default gh;