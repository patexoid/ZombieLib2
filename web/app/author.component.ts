/**
 * Created by Alexey on 9/5/2016.
 */
import {Component, Input} from "@angular/core";
import {Author} from "./Author";
import {Book} from "./Book";
import {AuthorService} from "./author.service";

@Component({
    selector: 'lib-author',
    template: `
<div *ngIf="_author" class="main">
    <h1 >{{_author.name}}</h1>
        <div>{{_author.descr}}</div>
        <div class="sequence">
            <div *ngFor="let sequence of _author.sequences" >
               {{sequence.name}}
                <div *ngFor="let bookSequence of sequence.bookSequences"
                 class="bookSequence"
                 [class.selected]="bookSequence.book === selectedBook" 
                 (click)="onSelect(bookSequence.book)">
                   {{bookSequence.seqOrder}} {{bookSequence.book.title}}
                </div>
            </div>
            <div *ngFor="let book of _author.booksNoSequence" 
            [class.selected]="book === selectedBook" 
                 (click)="onSelect(book)">
                {{book.title}}
            </div>
        </div>
<lib-book  *ngIf="selectedBook" [book] = "selectedBook"> </lib-book>
</div>
`,
    styles: [`
.main {
    border: 4px double black;
}
h1 {
    text-align: center;
}
.bookSequence{
    padding: 0;
    margin-left: 20px;
}
.selected {
  background-color: #CFD8DC !important;
  color: white;
}

`]
})
export class AuthorComponent {


    constructor(private authorsService: AuthorService) {
    }

    _author: Author;

    selectedBook: Book;

    @Input()
    set author(author: Author) {
        this.selectedBook = null;
        this._author = author;
        this.authorsService.getAuthor(author.id).then(author=> {
            this._author = author;
        })
    }


    onSelect(book: Book): void {
        this.selectedBook = book;
    }
}
