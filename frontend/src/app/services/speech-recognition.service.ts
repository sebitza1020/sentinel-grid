import { Injectable, OnDestroy } from '@angular/core';
import { BehaviorSubject, Observable, throwError } from 'rxjs';

export type SpeechRecognitionFailureCode =
  | 'unsupported'
  | 'busy'
  | 'permission'
  | 'no-speech'
  | 'audio-capture'
  | 'network'
  | 'aborted'
  | 'device';

export class SpeechRecognitionFailure extends Error {
  constructor(
    readonly code: SpeechRecognitionFailureCode,
    message: string,
  ) {
    super(message);
    this.name = 'SpeechRecognitionFailure';
  }
}

interface ActiveSession {
  recognition: SpeechRecognition;
  cancel(): void;
}

@Injectable({ providedIn: 'root' })
export class SpeechRecognitionService implements OnDestroy {
  readonly isListening$ = new BehaviorSubject(false);
  private activeSession: ActiveSession | null = null;

  get supported(): boolean {
    return !!this.recognitionConstructor();
  }

  startListening(): Observable<string> {
    const Recognition = this.recognitionConstructor();
    if (!Recognition) {
      return throwError(
        () =>
          new SpeechRecognitionFailure(
            'unsupported',
            'Native speech recognition is unavailable in this browser.',
          ),
      );
    }
    if (this.activeSession) {
      return throwError(
        () => new SpeechRecognitionFailure('busy', 'The voice listener is already active.'),
      );
    }

    return new Observable<string>((observer) => {
      const recognition = new Recognition();
      let finalized = false;

      const detach = (): void => {
        recognition.onresult = null;
        recognition.onerror = null;
        recognition.onend = null;
      };
      const finish = (): void => {
        if (finalized) return;
        finalized = true;
        detach();
        if (this.activeSession?.recognition === recognition) {
          this.activeSession = null;
        }
        this.isListening$.next(false);
      };
      const cancel = (): void => {
        if (finalized) return;
        detach();
        try {
          recognition.abort();
        } finally {
          finish();
          observer.complete();
        }
      };

      recognition.continuous = false;
      recognition.interimResults = false;
      recognition.maxAlternatives = 1;
      recognition.lang = 'en-US';

      recognition.onresult = (event) => {
        const transcripts: string[] = [];
        for (let index = event.resultIndex; index < event.results.length; index++) {
          const result = event.results[index];
          if (result?.isFinal && result[0]?.transcript) {
            transcripts.push(result[0].transcript.trim());
          }
        }
        const transcript = transcripts.filter(Boolean).join(' ').trim();
        if (!transcript) return;

        finish();
        observer.next(transcript);
        observer.complete();
      };
      recognition.onerror = (event) => {
        const failure = this.normalizeError(event.error);
        finish();
        observer.error(failure);
      };
      recognition.onend = () => {
        if (finalized) return;
        finish();
        observer.error(
          new SpeechRecognitionFailure('no-speech', 'No speech was detected. Please try again.'),
        );
      };

      this.activeSession = { recognition, cancel };
      try {
        recognition.start();
        this.isListening$.next(true);
      } catch {
        finish();
        observer.error(
          new SpeechRecognitionFailure('device', 'The microphone listener could not be started.'),
        );
      }

      return cancel;
    });
  }

  stopListening(): void {
    this.activeSession?.recognition.stop();
  }

  abortListening(): void {
    this.activeSession?.cancel();
  }

  ngOnDestroy(): void {
    this.abortListening();
    this.isListening$.complete();
  }

  private recognitionConstructor(): SpeechRecognitionConstructor | undefined {
    return window.SpeechRecognition ?? window.webkitSpeechRecognition;
  }

  private normalizeError(error: string): SpeechRecognitionFailure {
    switch (error) {
      case 'not-allowed':
      case 'service-not-allowed':
        return new SpeechRecognitionFailure(
          'permission',
          'Microphone permission was denied by the browser.',
        );
      case 'no-speech':
        return new SpeechRecognitionFailure(
          'no-speech',
          'No speech was detected. Please try again.',
        );
      case 'audio-capture':
        return new SpeechRecognitionFailure('audio-capture', 'No usable microphone is available.');
      case 'network':
        return new SpeechRecognitionFailure(
          'network',
          'The browser speech-recognition service is offline.',
        );
      case 'aborted':
        return new SpeechRecognitionFailure('aborted', 'Voice capture was cancelled.');
      default:
        return new SpeechRecognitionFailure(
          'device',
          'The browser could not process the voice input.',
        );
    }
  }
}
