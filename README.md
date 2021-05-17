# Ajanottaja - A simple time keeping tool

> Try to imagine a life without timekeeping. You probably can’t. You know the month, the year, the day of the week. There is a clock on your wall or the dashboard of your car. You have a schedule, a calendar, a time for dinner or a movie. Yet all around you, timekeeping is ignored. Birds are not late. A dog does not check its watch. Deer do not fret over passing birthdays. an alone measures time. Man alone chimes the hour. And, because of this, man alone suffers a paralyzing fear that no other creature endures. A fear of time running out.
</br>― Mitch Albom, The Time Keeper


The name _Ajanottaja_ is the Finnish word for timekeeper.
It is chosen as a nod to the Finnish Clojure shop [Metosin's](https://www.metosin.fi/en/) [library](https://github.com/metosin) naming scheme, many of which is used in this project.

## Rationale

_Ajanottaja_ is a super simple time keeping tool built with the hubmle worker in mind.
Most time tracking solutions are built to help employers monitor employee work time.
Many of them feel over engineered or simply cludgy to use as an employee.
Ajanottaja is your friendly simple time keeper that will help you track your worked time.
Ajanottaja can tell you how many hours you are owed, or how many hours you've falled behind.
In time it will tell you which projects you worked on, but only if you want.

Some solutions try to automate the time tracking, Ajanottaja will simply do what you tell it to.
It won't care that you opened up that Twitter tab to rest your brain for five minutes, neither should anyone else.
It will not automatically detect that you started working on a new project.
Context switching is expensive, so you shouldn't do it more than once of twice a day anyway.



## Deploying


## Development

Create a `.secrets.edn` file in the root of the project.
This file should contain development credentials including db password and api token.

```clojure
;; Example secrets config for use in development only.
;; Should be overwritten with a custom .secret.edn file in other environments.
{:db {:password "supersecretdev"}
 :server {:api-token "kGTH5AE02gBKm8wWMpYKDVFW8CYVIBRO6MPeEcYJYG33ikOCNiz0x0fIQNdU4o1N"}}
```


## Security



# License

Licensed under AGPLv3, see [License](/LICENSE).


In short this means you can:

1. Copy, run, and redistribute the code for free
2. Modify the code and run a public service, in which case source code MUST be released
3. Modify the code and run a private service (e.g. for yourself or your family) without releasing source code, but it is nice if you do
4. You must retain the copyright in any modified work

The AGPLv3 license was chosen to keep the project and any forks open for the public good.