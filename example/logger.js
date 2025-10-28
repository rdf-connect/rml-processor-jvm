import { Processor } from "@rdfc/js-runner";

export class Logger extends Processor {
    async startReader(reader) {
        this.logger.info("Starting with reader " + reader.uri);
        for await (const msg of reader.strings()) {
            console.log(reader.uri + "\n" + msg);
            this.logger.info(reader.uri + "\n" + msg);
        }
    }
    async init() {
    }
    async transform() {
        this.logger.info("Transform logger with " + this.streams.length + " readers");
        await Promise.allSettled(this.streams.map(r => this.startReader(r)));
    }
    async produce() {
    }
}
