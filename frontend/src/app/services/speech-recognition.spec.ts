import { SpeechRecognitionFailure, SpeechRecognitionService } from './speech-recognition.service';

class FakeRecognition {
  static instances: FakeRecognition[] = [];

  continuous = true;
  interimResults = true;
  lang = '';
  maxAlternatives = 0;
  onend: ((this: SpeechRecognition, event: Event) => unknown) | null = null;
  onerror: ((this: SpeechRecognition, event: SpeechRecognitionErrorEvent) => unknown) | null = null;
  onresult: ((this: SpeechRecognition, event: SpeechRecognitionEvent) => unknown) | null = null;
  started = false;
  stopped = false;
  aborted = false;

  constructor() {
    FakeRecognition.instances.push(this);
  }

  start(): void {
    this.started = true;
  }

  stop(): void {
    this.stopped = true;
  }

  abort(): void {
    this.aborted = true;
  }

  emitResult(transcript: string): void {
    const alternative = { transcript, confidence: 0.9 };
    const result = {
      0: alternative,
      isFinal: true,
      length: 1,
      item: () => alternative,
    };
    const results = {
      0: result,
      length: 1,
      item: () => result,
    };
    this.onresult?.call(
      this as unknown as SpeechRecognition,
      {
        resultIndex: 0,
        results,
      } as unknown as SpeechRecognitionEvent,
    );
  }

  emitError(error: string): void {
    this.onerror?.call(
      this as unknown as SpeechRecognition,
      {
        error,
        message: error,
      } as SpeechRecognitionErrorEvent,
    );
  }
}

describe('SpeechRecognitionService', () => {
  beforeEach(() => {
    FakeRecognition.instances = [];
    Object.defineProperty(window, 'SpeechRecognition', {
      configurable: true,
      value: FakeRecognition,
    });
    Object.defineProperty(window, 'webkitSpeechRecognition', {
      configurable: true,
      value: undefined,
    });
  });

  afterEach(() => {
    Reflect.deleteProperty(window, 'SpeechRecognition');
    Reflect.deleteProperty(window, 'webkitSpeechRecognition');
  });

  it('configures one-shot English recognition and emits the final transcript', () => {
    const service = new SpeechRecognitionService();
    let transcript = '';

    service.startListening().subscribe((value) => (transcript = value));
    const recognition = FakeRecognition.instances[0];

    expect(recognition.started).toBe(true);
    expect(recognition.continuous).toBe(false);
    expect(recognition.interimResults).toBe(false);
    expect(recognition.maxAlternatives).toBe(1);
    expect(recognition.lang).toBe('en-US');
    expect(service.isListening$.value).toBe(true);

    recognition.emitResult(' Send Razor-12 to Bucharest ');

    expect(transcript).toBe('Send Razor-12 to Bucharest');
    expect(service.isListening$.value).toBe(false);
  });

  it('falls back to webkitSpeechRecognition and supports manual stop', () => {
    Object.defineProperty(window, 'SpeechRecognition', {
      configurable: true,
      value: undefined,
    });
    Object.defineProperty(window, 'webkitSpeechRecognition', {
      configurable: true,
      value: FakeRecognition,
    });
    const service = new SpeechRecognitionService();

    service.startListening().subscribe();
    service.stopListening();

    expect(FakeRecognition.instances[0].stopped).toBe(true);
  });

  it('normalizes permission errors and prevents concurrent listeners', () => {
    const service = new SpeechRecognitionService();
    let permissionError: SpeechRecognitionFailure | undefined;
    let busyError: SpeechRecognitionFailure | undefined;

    service.startListening().subscribe({ error: (error) => (permissionError = error) });
    service.startListening().subscribe({ error: (error) => (busyError = error) });
    FakeRecognition.instances[0].emitError('not-allowed');

    expect(busyError?.code).toBe('busy');
    expect(permissionError?.code).toBe('permission');
  });

  it('reports unsupported browsers and aborts active recognition on destroy', () => {
    Object.defineProperty(window, 'SpeechRecognition', {
      configurable: true,
      value: undefined,
    });
    const unsupported = new SpeechRecognitionService();
    let unsupportedError: SpeechRecognitionFailure | undefined;

    unsupported.startListening().subscribe({ error: (error) => (unsupportedError = error) });
    expect(unsupportedError?.code).toBe('unsupported');

    Object.defineProperty(window, 'SpeechRecognition', {
      configurable: true,
      value: FakeRecognition,
    });
    const active = new SpeechRecognitionService();
    active.startListening().subscribe();
    active.ngOnDestroy();

    expect(FakeRecognition.instances[0].aborted).toBe(true);
  });
});
